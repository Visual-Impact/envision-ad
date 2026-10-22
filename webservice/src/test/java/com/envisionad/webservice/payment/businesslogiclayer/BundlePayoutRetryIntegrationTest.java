package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.InvalidRequestException;
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
import static org.mockito.Mockito.*;

/**
 * The insufficient-funds retry against a real Postgres ledger: which rows the job selects,
 * how it updates them in place, and that a retry can never pay an owner twice.
 *
 * <p>Stripe is stubbed with {@code mockStatic}, which is thread-local. That works here only
 * because the service is called directly on the test thread, never through HTTP; it also keeps
 * this class on the shared context ({@code BaseIntegrationTest} forbids new {@code @MockitoBean}s).
 */
class BundlePayoutRetryIntegrationTest extends BaseIntegrationTest {

    private static final String OWNER = "retry-it-owner";
    private static final String ACCOUNT = "acct_retry_it";
    private static final String SUBSCRIPTION_ID = "retry-it-subscription";

    @Autowired private BundlePayoutService bundlePayoutService;
    @Autowired private BundlePayoutRepository payoutRepository;
    @Autowired private BundleSubscriptionItemRepository itemRepository;
    @Autowired private StripeAccountRepository stripeAccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        StripeAccount account = new StripeAccount();
        account.setBusinessId(OWNER);
        account.setStripeAccountId(ACCOUNT);
        account.setOnboardingComplete(true);
        stripeAccountRepository.save(account);
    }

    @AfterEach
    void cleanUp() {
        payoutRepository.deleteAll(payoutRepository.findAllBySubscriptionId(SUBSCRIPTION_ID));
        itemRepository.deleteAll(itemRepository.findAllBySubscriptionId(SUBSCRIPTION_ID));
        stripeAccountRepository.findByBusinessId(OWNER).ifPresent(stripeAccountRepository::delete);
    }

    /**
     * The whole live-mode story for the D30 case: the discount exceeds the platform fee, so the
     * transfer comes from the platform balance, which hasn't settled yet. A day later it has, and
     * the retry pays the owner, with the same idempotency key and the same parameters (Stripe
     * rejects a reused key sent with different ones).
     */
    @Test
    void whenABalanceFundedPayoutFailsForInsufficientFunds_thenItIsPaidByTheRetryADayLater() {
        String invoiceId = newInvoiceId();
        givenItem("60.00"); // owed 42.00 net
        TransferCreateParams firstParams;
        RequestOptions firstOptions;

        try (MockedStatic<InvoicePayment> payments = mockStatic(InvoicePayment.class);
             MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenInvoicePaidBy(payments, "ch_discounted", 3000L); // collected 30.00 after discount
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(insufficientFunds());

            bundlePayoutService.payOutInvoice(invoiceId, SUBSCRIPTION_ID);

            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
            transfers.verify(() -> Transfer.create(params.capture(), options.capture()));
            firstParams = params.getValue();
            firstOptions = options.getValue();
        }

        BundlePayout failed = payoutFor(invoiceId);
        assertEquals(BundlePayoutStatus.FAILED, failed.getStatus());
        assertEquals("balance_insufficient", failed.getFailureCode());
        assertNull(failed.getStripeSourceChargeId(), "the D30 case is funded from the balance");

        // Inside the 24h idempotency window a retry could only replay the cached error.
        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            bundlePayoutService.retryInsufficientFundsPayouts();
            transfers.verifyNoInteractions();
        }

        lastAttemptedHoursAgo(invoiceId, 25);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_retried", Map.of()));

            bundlePayoutService.retryInsufficientFundsPayouts();

            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
            transfers.verify(() -> Transfer.create(params.capture(), options.capture()));
            assertEquals(firstParams.toMap(), params.getValue().toMap());
            assertEquals(firstOptions.getIdempotencyKey(), options.getValue().getIdempotencyKey());
            assertEquals(4200L, params.getValue().getAmount());
        }

        BundlePayout paid = payoutFor(invoiceId);
        assertEquals(BundlePayoutStatus.PAID, paid.getStatus());
        assertEquals("tr_retried", paid.getStripeTransferId());
        assertNull(paid.getFailureCode());
        assertNull(paid.getFailureReason());
        assertEquals(failed.getId(), paid.getId(), "updated in place, never a second ledger row");
    }

    /** A sourced transfer is retried against the same charge, read from the ledger row. */
    @Test
    void whenTheFailedPayoutWasTiedToACharge_thenTheRetryTiesItToTheSameCharge() {
        String invoiceId = givenFailedPayout("balance_insufficient", "ch_original", 25, 25);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_sourced", Map.of()));

            bundlePayoutService.retryInsufficientFundsPayouts();

            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
            transfers.verify(() -> Transfer.create(params.capture(), options.capture()));
            assertEquals("ch_original", params.getValue().getSourceTransaction());
            assertEquals(700L, params.getValue().getAmount(), "the stored amount, never recomputed");
            assertEquals(ACCOUNT, params.getValue().getDestination());
            assertEquals("payout-" + invoiceId + "-" + OWNER, options.getValue().getIdempotencyKey());
        }
        assertEquals(BundlePayoutStatus.PAID, payoutFor(invoiceId).getStatus());
    }

    /**
     * The double-payment guard once the idempotency key has expired. A transfer that went
     * through but whose success was never written down must be adopted, not repeated.
     */
    @Test
    void whenStripeAlreadyHasATransferForThisPayout_thenTheRetryRecordsItAndSendsNothing() {
        String invoiceId = givenFailedPayout("balance_insufficient", null, 30, 30);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            TransferCollection existing = collectionOf(
                    transfer("tr_other_invoice", Map.of("stripeInvoiceId", "in_other", "mediaOwnerBusinessId", OWNER)),
                    transfer("tr_already_sent", Map.of("stripeInvoiceId", invoiceId, "mediaOwnerBusinessId", OWNER)));
            transfers.when(() -> Transfer.list(any(TransferListParams.class))).thenReturn(existing);

            bundlePayoutService.retryInsufficientFundsPayouts();

            ArgumentCaptor<TransferListParams> listParams = ArgumentCaptor.forClass(TransferListParams.class);
            transfers.verify(() -> Transfer.list(listParams.capture()));
            assertEquals(ACCOUNT, listParams.getValue().getDestination());
            transfers.verify(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)), never());
        }

        BundlePayout payout = payoutFor(invoiceId);
        assertEquals(BundlePayoutStatus.PAID, payout.getStatus());
        assertEquals("tr_already_sent", payout.getStripeTransferId());
    }

    @Test
    void whenTheRetryIsShortOfFundsAgain_thenItWaitsAnotherDay() {
        String invoiceId = givenFailedPayout("balance_insufficient", null, 25, 25);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(insufficientFunds());

            bundlePayoutService.retryInsufficientFundsPayouts();
            bundlePayoutService.retryInsufficientFundsPayouts();

            transfers.verify(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)), times(1));
        }

        BundlePayout payout = payoutFor(invoiceId);
        assertEquals(BundlePayoutStatus.FAILED, payout.getStatus());
        assertEquals("balance_insufficient", payout.getFailureCode());
        assertTrue(payout.getLastAttemptAt().isAfter(LocalDateTime.now().minusMinutes(5)));
    }

    /** Any other rejection is not a matter of waiting, so the row drops out of the retries. */
    @Test
    void whenTheRetryIsRejectedForAnotherReason_thenTheRowIsNeverRetriedAgain() {
        String invoiceId = givenFailedPayout("balance_insufficient", null, 25, 25);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            givenNoExistingTransfers(transfers);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new InvalidRequestException("No such destination", "destination", null,
                            "resource_missing", 400, null));
            bundlePayoutService.retryInsufficientFundsPayouts();
        }

        assertEquals("resource_missing", payoutFor(invoiceId).getFailureCode());

        lastAttemptedHoursAgo(invoiceId, 48);
        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            bundlePayoutService.retryInsufficientFundsPayouts();
            transfers.verifyNoInteractions();
        }
    }

    @Test
    void whenFailureWasNotInsufficientFunds_orThePayoutIsTooOld_thenItIsNotRetried() {
        givenFailedPayout("account_invalid", null, 48, 48);
        givenFailedPayout(null, null, 48, 48); // a row from before failure codes were stored
        givenFailedPayout("balance_insufficient", null, 31 * 24, 25); // past the 30-day max age

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            bundlePayoutService.retryInsufficientFundsPayouts();
            transfers.verifyNoInteractions();
        }
    }

    @Test
    void whenTheOwnerNoLongerHasAnOnboardedAccount_thenTheRowIsLeftAlone() {
        String invoiceId = givenFailedPayout("balance_insufficient", null, 25, 25);
        StripeAccount account = stripeAccountRepository.findByBusinessId(OWNER).orElseThrow();
        account.setOnboardingComplete(false);
        stripeAccountRepository.save(account);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            bundlePayoutService.retryInsufficientFundsPayouts();
            transfers.verifyNoInteractions();
        }
        assertEquals(BundlePayoutStatus.FAILED, payoutFor(invoiceId).getStatus());
    }

    /** Stripe unreachable mid-run: that row is retried next run, and the others still go. */
    @Test
    void whenStripeIsUnreachableForOneRow_thenThatRowIsUntouchedAndTheOthersAreStillRetried() {
        String unlucky = givenFailedPayout("balance_insufficient", null, 26, 26);
        String lucky = givenFailedPayout("balance_insufficient", null, 25, 25);

        TransferCollection none = collectionOf();
        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            transfers.when(() -> Transfer.list(any(TransferListParams.class)))
                    .thenThrow(new ApiConnectionException("Stripe unreachable"))
                    .thenReturn(none);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(transfer("tr_lucky", Map.of()));

            bundlePayoutService.retryInsufficientFundsPayouts();
        }

        BundlePayout untouched = payoutFor(unlucky);
        assertEquals(BundlePayoutStatus.FAILED, untouched.getStatus());
        assertTrue(untouched.getLastAttemptAt().isBefore(LocalDateTime.now().minusHours(24)),
                "not counted as an attempt, so the next run tries it again");
        assertEquals(BundlePayoutStatus.PAID, payoutFor(lucky).getStatus());
    }

    // --- helpers -----------------------------------------------------------

    private String newInvoiceId() {
        return "in_retry_it_" + UUID.randomUUID();
    }

    private void givenItem(String monthlyAmount) {
        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(SUBSCRIPTION_ID);
        item.setMediaId(UUID.randomUUID());
        item.setMediaOwnerBusinessId(OWNER);
        item.setMonthlyAmount(new BigDecimal(monthlyAmount));
        itemRepository.save(item);
    }

    /** A FAILED 7.00 payout row, created and last attempted the given number of hours ago. */
    private String givenFailedPayout(String failureCode, String sourceChargeId,
            int createdHoursAgo, int lastAttemptedHoursAgo) {
        String invoiceId = newInvoiceId();
        BundlePayout payout = new BundlePayout();
        payout.setStripeInvoiceId(invoiceId);
        payout.setSubscriptionId(SUBSCRIPTION_ID);
        payout.setMediaOwnerBusinessId(OWNER);
        payout.setGrossAmount(new BigDecimal("10.00"));
        payout.setAmount(new BigDecimal("7.00"));
        payout.setStatus(BundlePayoutStatus.FAILED);
        payout.setFailureCode(failureCode);
        payout.setFailureReason("seeded failure");
        payout.setStripeSourceChargeId(sourceChargeId);
        payout.setLastAttemptAt(LocalDateTime.now());
        payoutRepository.save(payout);

        // created_at is insert-only on the entity, so age the row in SQL.
        jdbcTemplate.update("UPDATE bundle_payouts SET created_at = ? WHERE stripe_invoice_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(createdHoursAgo)), invoiceId);
        lastAttemptedHoursAgo(invoiceId, lastAttemptedHoursAgo);
        return invoiceId;
    }

    private void lastAttemptedHoursAgo(String invoiceId, int hours) {
        jdbcTemplate.update("UPDATE bundle_payouts SET last_attempt_at = ? WHERE stripe_invoice_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(hours)), invoiceId);
    }

    private BundlePayout payoutFor(String invoiceId) {
        List<BundlePayout> rows = payoutRepository.findAllByStripeInvoiceId(invoiceId);
        assertEquals(1, rows.size());
        return rows.get(0);
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

    private static InvalidRequestException insufficientFunds() {
        return new InvalidRequestException("You have insufficient available funds in your Stripe account.",
                null, null, "balance_insufficient", 400, null);
    }
}
