package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Account;
import com.stripe.model.InvoicePayment;
import com.stripe.model.InvoicePaymentCollection;
import com.stripe.model.Transfer;
import com.stripe.model.TransferCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferListParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The onboarding hold (Terms of Service §7) against a real Postgres ledger: an owner whose Stripe
 * Connect onboarding is incomplete when an invoice is paid has their share held, gets it once
 * onboarding completes, and loses it only if that takes more than 90 days.
 *
 * <p>Stripe is stubbed with {@code mockStatic}, which is thread-local; this works because the
 * service is called directly on the test thread (see {@code BundlePayoutRetryIntegrationTest}).
 */
class BundlePayoutHoldIntegrationTest extends BaseIntegrationTest {

    private static final String LATE_OWNER = "hold-it-late-owner";
    private static final String LATE_ACCOUNT = "acct_hold_it_late";
    private static final String READY_OWNER = "hold-it-ready-owner";
    private static final String READY_ACCOUNT = "acct_hold_it_ready";
    private static final String SUBSCRIPTION_ID = "hold-it-subscription";
    private static final String CHARGE_ID = "ch_hold_it";

    @Autowired private BundlePayoutService bundlePayoutService;
    @Autowired private BundlePayoutRepository payoutRepository;
    @Autowired private BundleSubscriptionItemRepository itemRepository;
    @Autowired private StripeAccountRepository stripeAccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        payoutRepository.deleteAll(payoutRepository.findAllBySubscriptionId(SUBSCRIPTION_ID));
        itemRepository.deleteAll(itemRepository.findAllBySubscriptionId(SUBSCRIPTION_ID));
        for (String owner : List.of(LATE_OWNER, READY_OWNER)) {
            stripeAccountRepository.findByBusinessId(owner).ifPresent(stripeAccountRepository::delete);
        }
    }

    /**
     * The whole story. One invoice, two owners: the onboarded one is paid at once; the other's
     * share is held, survives a redelivered invoice.paid untouched, and is transferred when their
     * onboarding completes, with the amount, funding source and idempotency key a normal payout
     * would have used.
     */
    @Test
    void whenAnOwnerIsNotOnboardedAtPayment_thenTheirShareIsHeldAndPaidOnceOnboardingCompletes() {
        String invoiceId = newInvoiceId();
        givenAccount(READY_OWNER, READY_ACCOUNT, true);
        givenAccount(LATE_OWNER, LATE_ACCOUNT, false);
        givenItem(READY_OWNER, "20.00");
        givenItem(LATE_OWNER, "10.00"); // owed 7.00 net

        try (MockedStatic<InvoicePayment> payments = mockStatic(InvoicePayment.class);
             MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenInvoicePaidBy(payments, CHARGE_ID, 3000L);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_ready", Map.of()));

            bundlePayoutService.payOutInvoice(invoiceId, SUBSCRIPTION_ID);
            // Stripe redelivers invoice.paid as a matter of course
            bundlePayoutService.payOutInvoice(invoiceId, SUBSCRIPTION_ID);

            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)), times(1));
            assertEquals(READY_ACCOUNT, params.getValue().getDestination());
        }

        assertEquals(BundlePayoutStatus.PAID, payoutFor(invoiceId, READY_OWNER).getStatus());
        BundlePayout held = payoutFor(invoiceId, LATE_OWNER);
        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, held.getStatus());
        assertEquals(0, new BigDecimal("7.00").compareTo(held.getAmount()));
        assertEquals(CHARGE_ID, held.getStripeSourceChargeId());
        assertNull(held.getLastAttemptAt());
        assertNull(held.getHoldEndedAt());

        // A week later the owner finishes onboarding and account.updated arrives.
        createdDaysAgo(held, 7);
        completeOnboarding(LATE_OWNER);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_released", Map.of()));

            bundlePayoutService.releaseHeldPayouts(LATE_OWNER);

            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
            transfers.verify(() -> Transfer.create(params.capture(), options.capture()));
            assertEquals(700L, params.getValue().getAmount(), "the stored amount, never recomputed");
            assertEquals(LATE_ACCOUNT, params.getValue().getDestination());
            assertEquals(CHARGE_ID, params.getValue().getSourceTransaction(), "the funding decided at payment");
            assertEquals(invoiceId, params.getValue().getMetadata().get("stripeInvoiceId"));
            assertEquals("payout-" + invoiceId + "-" + LATE_OWNER, options.getValue().getIdempotencyKey());
        }

        BundlePayout paid = payoutFor(invoiceId, LATE_OWNER);
        assertEquals(BundlePayoutStatus.PAID, paid.getStatus());
        assertEquals("tr_released", paid.getStripeTransferId());
        assertEquals(held.getId(), paid.getId(), "updated in place, never a second ledger row");
        assertNotNull(paid.getHoldEndedAt());
        assertNotNull(paid.getLastAttemptAt());

        // A second account.updated finds nothing left to release.
        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            bundlePayoutService.releaseHeldPayouts(LATE_OWNER);
            transfers.verifyNoInteractions();
        }
    }

    @Test
    void whenTheOwnerIsStillNotOnboarded_thenReleasingDoesNothing() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, false);
        String invoiceId = givenHeldPayout(LATE_OWNER, CHARGE_ID, 10);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            bundlePayoutService.releaseHeldPayouts(LATE_OWNER);
            transfers.verifyNoInteractions();
        }
        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, payoutFor(invoiceId, LATE_OWNER).getStatus());
    }

    /** The sweep is the backstop for an account.updated webhook that never arrived. */
    @Test
    void whenTheOwnerIsOnboardedButNoWebhookReleasedTheShare_thenTheSweepPaysIt() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, true);
        String invoiceId = givenHeldPayout(LATE_OWNER, null, 30);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_swept", Map.of()));

            bundlePayoutService.releaseOrExpireHeldPayouts();

            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)));
            assertNull(params.getValue().getSourceTransaction(), "a balance-funded share stays balance-funded");
        }
        assertEquals("tr_swept", payoutFor(invoiceId, LATE_OWNER).getStripeTransferId());
    }

    /** Day 89: still inside the hold, so nothing is expired and Stripe is not even asked. */
    @Test
    void whenTheShareHasBeenHeldFor89Days_thenItIsStillHeld() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, false);
        String invoiceId = givenHeldPayout(LATE_OWNER, CHARGE_ID, 89);

        try (MockedStatic<Account> accounts = mockStatic(Account.class);
             MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            bundlePayoutService.releaseOrExpireHeldPayouts();
            accounts.verifyNoInteractions();
            transfers.verifyNoInteractions();
        }
        BundlePayout payout = payoutFor(invoiceId, LATE_OWNER);
        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, payout.getStatus());
        assertNull(payout.getHoldEndedAt());
    }

    /** Day 91 and Stripe confirms onboarding is still incomplete: the amount is no longer owed. */
    @Test
    void whenTheShareHasBeenHeldFor91DaysAndStripeConfirmsOnboardingIsIncomplete_thenItExpires() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, false);
        String overdue = givenHeldPayout(LATE_OWNER, CHARGE_ID, 91);
        String recent = givenHeldPayout(LATE_OWNER, CHARGE_ID, 20);

        try (MockedStatic<Account> accounts = mockStatic(Account.class);
             MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            Account remote = remoteAccount(LATE_ACCOUNT, true, false, false);
            accounts.when(() -> Account.retrieve(LATE_ACCOUNT)).thenReturn(remote);

            bundlePayoutService.releaseOrExpireHeldPayouts();

            accounts.verify(() -> Account.retrieve(LATE_ACCOUNT));
            transfers.verifyNoInteractions();
        }

        BundlePayout expired = payoutFor(overdue, LATE_OWNER);
        assertEquals(BundlePayoutStatus.EXPIRED_NOT_ONBOARDED, expired.getStatus());
        assertNotNull(expired.getHoldEndedAt());
        assertNull(expired.getStripeTransferId());
        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, payoutFor(recent, LATE_OWNER).getStatus(),
                "a share inside its 90 days is untouched");
        assertTrue(stripeAccountRepository.findByBusinessId(LATE_OWNER).orElseThrow().isOnboardingComplete(),
                "what Stripe said is stored");

        // Expired is terminal: finishing onboarding afterwards pays nothing for that invoice.
        completeOnboarding(LATE_OWNER);
        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_recent", Map.of()));

            bundlePayoutService.releaseHeldPayouts(LATE_OWNER);

            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)), times(1));
            assertEquals(recent, params.getValue().getMetadata().get("stripeInvoiceId"));
        }
        assertEquals(BundlePayoutStatus.EXPIRED_NOT_ONBOARDED, payoutFor(overdue, LATE_OWNER).getStatus());
    }

    @Test
    void whenTheShareHasBeenHeldFor91DaysAndTheOwnerNeverConnectedStripe_thenItExpires() {
        String invoiceId = givenHeldPayout(LATE_OWNER, CHARGE_ID, 91);

        try (MockedStatic<Account> accounts = mockStatic(Account.class)) {
            bundlePayoutService.releaseOrExpireHeldPayouts();
            accounts.verifyNoInteractions();
        }
        assertEquals(BundlePayoutStatus.EXPIRED_NOT_ONBOARDED, payoutFor(invoiceId, LATE_OWNER).getStatus());
    }

    /**
     * The owner did finish in time, but account.updated was missed and our flags are stale.
     * Expiring on those flags would forfeit money that is owed, so Stripe is asked first.
     */
    @Test
    void whenOurFlagsAreStaleButStripeSaysOnboardingIsComplete_thenTheOverdueShareIsPaidNotExpired() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, false);
        String invoiceId = givenHeldPayout(LATE_OWNER, CHARGE_ID, 91);

        try (MockedStatic<Account> accounts = mockStatic(Account.class);
             MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            Account remote = remoteAccount(LATE_ACCOUNT, true, true, true);
            accounts.when(() -> Account.retrieve(LATE_ACCOUNT)).thenReturn(remote);
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_in_time", Map.of()));

            bundlePayoutService.releaseOrExpireHeldPayouts();
        }

        BundlePayout payout = payoutFor(invoiceId, LATE_OWNER);
        assertEquals(BundlePayoutStatus.PAID, payout.getStatus());
        assertEquals("tr_in_time", payout.getStripeTransferId());
        StripeAccount synced = stripeAccountRepository.findByBusinessId(LATE_OWNER).orElseThrow();
        assertTrue(synced.isChargesEnabled() && synced.isPayoutsEnabled());
    }

    /** Nothing is forfeited on a guess: Stripe unreachable means try again next run. */
    @Test
    void whenStripeCannotBeAskedAtExpiry_thenTheShareStaysHeld() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, false);
        String invoiceId = givenHeldPayout(LATE_OWNER, CHARGE_ID, 91);

        try (MockedStatic<Account> accounts = mockStatic(Account.class)) {
            accounts.when(() -> Account.retrieve(anyString()))
                    .thenThrow(new ApiConnectionException("Stripe unreachable"));

            assertDoesNotThrow(() -> bundlePayoutService.releaseOrExpireHeldPayouts());
        }
        BundlePayout payout = payoutFor(invoiceId, LATE_OWNER);
        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, payout.getStatus());
        assertNull(payout.getHoldEndedAt());
    }

    /** An earlier release that went through on Stripe but was never recorded is adopted. */
    @Test
    void whenStripeAlreadyHasATransferForTheHeldShare_thenItIsRecordedAndNothingIsSent() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, true);
        String invoiceId = givenHeldPayout(LATE_OWNER, CHARGE_ID, 10);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            TransferCollection existing = collectionOf(
                    transfer("tr_already_sent", Map.of("stripeInvoiceId", invoiceId, "mediaOwnerBusinessId", LATE_OWNER)));
            transfers.when(() -> Transfer.list(any(TransferListParams.class))).thenReturn(existing);

            bundlePayoutService.releaseHeldPayouts(LATE_OWNER);

            transfers.verify(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)), never());
        }
        BundlePayout payout = payoutFor(invoiceId, LATE_OWNER);
        assertEquals(BundlePayoutStatus.PAID, payout.getStatus());
        assertEquals("tr_already_sent", payout.getStripeTransferId());
    }

    /**
     * A share held for 40 days, released from the platform balance, and short of funds: the
     * insufficient-funds retry still picks it up, because its 30 days count from the release.
     */
    @Test
    void whenAReleaseIsShortOfFunds_thenTheRetryJobPicksItUpEvenThoughTheInvoiceIsOlderThan30Days() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, true);
        String invoiceId = givenHeldPayout(LATE_OWNER, null, 40);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new InvalidRequestException("You have insufficient available funds in your Stripe account.",
                            null, null, "balance_insufficient", 400, null));

            bundlePayoutService.releaseHeldPayouts(LATE_OWNER);
        }

        BundlePayout failed = payoutFor(invoiceId, LATE_OWNER);
        assertEquals(BundlePayoutStatus.FAILED, failed.getStatus());
        assertEquals("balance_insufficient", failed.getFailureCode());
        assertNotNull(failed.getHoldEndedAt());

        jdbcTemplate.update("UPDATE bundle_payouts SET last_attempt_at = ?, hold_ended_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(25)),
                Timestamp.valueOf(LocalDateTime.now().minusHours(25)), failed.getId());

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_retried", Map.of()));

            bundlePayoutService.retryInsufficientFundsPayouts();
        }
        assertEquals(BundlePayoutStatus.PAID, payoutFor(invoiceId, LATE_OWNER).getStatus());
    }

    /** Any other rejection is an ordinary failure, left for a human like any other. */
    @Test
    void whenAReleaseIsRejectedForAnotherReason_thenTheShareIsRecordedFailed() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, true);
        String invoiceId = givenHeldPayout(LATE_OWNER, CHARGE_ID, 5);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new InvalidRequestException("No such destination", "destination", null,
                            "resource_missing", 400, null));

            assertDoesNotThrow(() -> bundlePayoutService.releaseHeldPayouts(LATE_OWNER));
        }

        BundlePayout failed = payoutFor(invoiceId, LATE_OWNER);
        assertEquals(BundlePayoutStatus.FAILED, failed.getStatus());
        assertEquals("resource_missing", failed.getFailureCode());
        assertTrue(failed.getFailureReason().startsWith("No such destination"));
    }

    /** Stripe unreachable mid-release: that share stays held for the next try; the rest still go. */
    @Test
    void whenStripeIsUnreachableForOneHeldShare_thenItStaysHeldAndTheOthersAreStillReleased() {
        givenAccount(LATE_OWNER, LATE_ACCOUNT, true);
        String unlucky = givenHeldPayout(LATE_OWNER, CHARGE_ID, 20);
        String lucky = givenHeldPayout(LATE_OWNER, CHARGE_ID, 10);

        TransferCollection none = collectionOf();
        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            transfers.when(() -> Transfer.list(any(TransferListParams.class)))
                    .thenThrow(new ApiConnectionException("Stripe unreachable"))
                    .thenReturn(none);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_lucky", Map.of()));

            bundlePayoutService.releaseHeldPayouts(LATE_OWNER);
        }

        BundlePayout untouched = payoutFor(unlucky, LATE_OWNER);
        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, untouched.getStatus());
        assertNull(untouched.getHoldEndedAt());
        assertEquals(BundlePayoutStatus.PAID, payoutFor(lucky, LATE_OWNER).getStatus());
    }

    // --- helpers -----------------------------------------------------------

    private String newInvoiceId() {
        return "in_hold_it_" + UUID.randomUUID();
    }

    private void givenAccount(String owner, String stripeAccountId, boolean complete) {
        StripeAccount account = new StripeAccount();
        account.setBusinessId(owner);
        account.setStripeAccountId(stripeAccountId);
        account.setOnboardingComplete(complete);
        account.setChargesEnabled(complete);
        account.setPayoutsEnabled(complete);
        stripeAccountRepository.save(account);
    }

    private void completeOnboarding(String owner) {
        StripeAccount account = stripeAccountRepository.findByBusinessId(owner).orElseThrow();
        account.setOnboardingComplete(true);
        account.setChargesEnabled(true);
        account.setPayoutsEnabled(true);
        stripeAccountRepository.save(account);
    }

    private void givenItem(String owner, String monthlyAmount) {
        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(SUBSCRIPTION_ID);
        item.setMediaId(UUID.randomUUID());
        item.setMediaOwnerBusinessId(owner);
        item.setMonthlyAmount(new BigDecimal(monthlyAmount));
        itemRepository.save(item);
    }

    /** A HELD 7.00 share for {@code owner}, created the given number of days ago. */
    private String givenHeldPayout(String owner, String sourceChargeId, int createdDaysAgo) {
        String invoiceId = newInvoiceId();
        BundlePayout payout = new BundlePayout();
        payout.setStripeInvoiceId(invoiceId);
        payout.setSubscriptionId(SUBSCRIPTION_ID);
        payout.setMediaOwnerBusinessId(owner);
        payout.setGrossAmount(new BigDecimal("10.00"));
        payout.setAmount(new BigDecimal("7.00"));
        payout.setStatus(BundlePayoutStatus.HELD_NOT_ONBOARDED);
        payout.setStripeSourceChargeId(sourceChargeId);
        createdDaysAgo(payoutRepository.save(payout), createdDaysAgo);
        return invoiceId;
    }

    /** created_at is insert-only on the entity, so age the row in SQL. */
    private void createdDaysAgo(BundlePayout payout, int days) {
        jdbcTemplate.update("UPDATE bundle_payouts SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(days)), payout.getId());
    }

    private BundlePayout payoutFor(String invoiceId, String owner) {
        List<BundlePayout> rows = payoutRepository.findAllByStripeInvoiceId(invoiceId).stream()
                .filter(p -> owner.equals(p.getMediaOwnerBusinessId()))
                .toList();
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private static Account remoteAccount(String id, boolean detailsSubmitted, boolean chargesEnabled,
            boolean payoutsEnabled) {
        Account account = new Account();
        account.setId(id);
        account.setDetailsSubmitted(detailsSubmitted);
        account.setChargesEnabled(chargesEnabled);
        account.setPayoutsEnabled(payoutsEnabled);
        return account;
    }

    private static void givenInvoicePaidBy(MockedStatic<InvoicePayment> payments, String chargeId, long amountPaid) {
        InvoicePayment.Payment payment = new InvoicePayment.Payment();
        payment.setType("charge");
        payment.setCharge(chargeId);
        InvoicePayment invoicePayment = new InvoicePayment();
        invoicePayment.setAmountPaid(amountPaid);
        invoicePayment.setPayment(payment);
        InvoicePaymentCollection collection = new InvoicePaymentCollection();
        collection.setData(List.of(invoicePayment));
        payments.when(() -> InvoicePayment.list(any(InvoicePaymentListParams.class))).thenReturn(collection);
    }

    private static void givenNoExistingTransfers(MockedStatic<Transfer> transfers) {
        TransferCollection none = collectionOf(); // built first: Mockito can't nest stubbing
        transfers.when(() -> Transfer.list(any(TransferListParams.class))).thenReturn(none);
    }

    /** A mock: a hand-built collection can't page, since it was never fetched through a client. */
    private static TransferCollection collectionOf(Transfer... transfers) {
        TransferCollection collection = mock(TransferCollection.class);
        when(collection.autoPagingIterable()).thenReturn(List.of(transfers));
        return collection;
    }

    private static Transfer transfer(String id, Map<String, String> metadata) {
        Transfer transfer = new Transfer();
        transfer.setId(id);
        transfer.setMetadata(metadata);
        return transfer;
    }
}
