package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.payment.exceptions.PayoutFundingLookupException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.InvoicePayment;
import com.stripe.model.InvoicePaymentCollection;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.TransferCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The transfer-splitting maths, which the brief singles out as easy to get subtly
 * wrong and hard to debug once it is moving real money.
 *
 * <p>Unit-level because every path here ends in a Stripe call, and the integration
 * harness cannot stub Stripe (see {@code BaseIntegrationTest}). It is also the only
 * place the multi-owner split is genuinely exercised: the seeded dev data has a
 * single owner who is also the buyer, so a live run proves plumbing, not arithmetic.
 *
 * <p>Every test sees the invoice as paid by one charge, {@link #CHARGE_ID}, large enough to
 * fund any payout here, unless it says otherwise through {@link #givenInvoicePaidBy}.
 */
@ExtendWith(MockitoExtension.class)
class BundlePayoutServiceUnitTest {

    private static final String INVOICE_ID = "in_test_123";
    private static final String SUBSCRIPTION_ID = "sub-local-abc";
    private static final String OWNER_A = "owner-aaa";
    private static final String OWNER_B = "owner-bbb";
    private static final String ACCOUNT_A = "acct_AAA";
    private static final String ACCOUNT_B = "acct_BBB";
    private static final String CHARGE_ID = "ch_test_123";

    private BundlePayoutServiceImpl service;

    @Mock private BundleSubscriptionItemRepository itemRepository;
    @Mock private StripeAccountRepository stripeAccountRepository;
    @Mock private BundlePayoutRepository payoutRepository;

    private MockedStatic<InvoicePayment> invoicePayments;

    @BeforeEach
    void setUp() {
        service = new BundlePayoutServiceImpl(itemRepository, stripeAccountRepository, payoutRepository);
        ReflectionTestUtils.setField(service, "platformFeePercent", 30);
        ReflectionTestUtils.setField(service, "retryMaxAge", Duration.ofDays(30));
        ReflectionTestUtils.setField(service, "holdMaxAge", Duration.ofDays(90));
        // Lenient: tests that never reach a transfer never look the charge up.
        invoicePayments = mockStatic(InvoicePayment.class, withSettings().strictness(Strictness.LENIENT));
        givenInvoicePaidBy(chargePayment(CHARGE_ID, 1_000_000L));
    }

    @AfterEach
    void tearDown() {
        invoicePayments.close();
    }

    @Test
    void whenInvoicePaid_withScreensAcrossTwoOwners_thenEachOwnerGetsTheirOwnSummedShare() {
        // Owner A owns two screens ($4.00 + $6.50), owner B owns one ($4.00).
        givenItems(item(OWNER_A, "4.00"), item(OWNER_B, "4.00"), item(OWNER_A, "6.50"));
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenOnboarded(OWNER_B, ACCOUNT_B);
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_1"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)), times(2));
            List<TransferCreateParams> sent = params.getAllValues();

            // A: (4.00 + 6.50) * 70% = 7.35 ; B: 4.00 * 70% = 2.80
            assertEquals(735L, sent.get(0).getAmount());
            assertEquals(ACCOUNT_A, sent.get(0).getDestination());
            assertEquals(280L, sent.get(1).getAmount());
            assertEquals(ACCOUNT_B, sent.get(1).getDestination());
            assertEquals("cad", sent.get(0).getCurrency());
        }
    }

    /**
     * The D30 guarantee. The subscription collected a discounted amount, but the items
     * hold each screen's full list price and must be paid out unscaled — the platform
     * absorbs the discount, not the media owners.
     */
    @Test
    void whenBundleWasDiscounted_thenPayoutIsBasedOnFullScreenPricesNotTheDiscountedTotal() {
        // 15 screens at $4.00 = $60.00 of items, on a subscription that charged $48.00.
        BundleSubscriptionItem[] items = new BundleSubscriptionItem[15];
        for (int i = 0; i < items.length; i++) {
            items[i] = item(OWNER_A, "4.00");
        }
        givenItems(items);
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_1"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)));
            // $60.00 * 70% = $42.00. Scaling by the 20% discount first would give
            // $33.60 and silently underpay the owner.
            assertEquals(4200L, params.getValue().getAmount());
        }
    }

    /**
     * A discount larger than the platform fee pays owners more than the invoice
     * collected (D30). Accepted rather than capped — and because Stripe caps
     * source_transaction transfers at the charge amount, this case alone is paid from
     * the platform balance.
     */
    @Test
    void whenDiscountExceedsPlatformFee_thenPayoutStillExceedsWhatWasCollectedAndIsNotCapped() {
        givenItems(item(OWNER_A, "60.00"));
        givenInvoicePaidBy(chargePayment(CHARGE_ID, 3000L)); // $30.00 after a 50% discount
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_1"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)));
            assertEquals(4200L, params.getValue().getAmount());
            assertNull(params.getValue().getSourceTransaction(),
                    "no source_transaction: Stripe caps those at the source charge amount");
        }
        assertNull(savedPayoutFor(OWNER_A).getStripeSourceChargeId());
    }

    /**
     * The live-mode fix. A card payment sits in the pending balance for days, so a transfer
     * from the available balance on invoice.paid fails. Tied to the charge, it goes through.
     */
    @Test
    void whenPayoutsFitInsideTheCharge_thenEveryTransferIsTiedToThatCharge() {
        givenItems(item(OWNER_A, "10.00"), item(OWNER_B, "10.00"));
        givenInvoicePaidBy(chargePayment(CHARGE_ID, 2000L)); // undiscounted: $20.00 collected
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenOnboarded(OWNER_B, ACCOUNT_B);
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_1"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)), times(2));
            assertEquals(CHARGE_ID, params.getAllValues().get(0).getSourceTransaction());
            assertEquals(CHARGE_ID, params.getAllValues().get(1).getSourceTransaction());
        }
        assertEquals(CHARGE_ID, savedPayoutFor(OWNER_A).getStripeSourceChargeId());
        assertNotNull(savedPayoutFor(OWNER_A).getLastAttemptAt());
    }

    /** Payouts fill the charge to the cent: still inside Stripe's cap. */
    @Test
    void whenPayoutsEqualTheChargeExactly_thenTheyAreStillTiedToIt() {
        givenItems(item(OWNER_A, "10.00"));
        givenInvoicePaidBy(chargePayment(CHARGE_ID, 700L)); // a 30% discount: collected exactly the net
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        assertEquals(CHARGE_ID, sentParams().get(0).getSourceTransaction());
    }

    /**
     * Stripe caps the SUM of transfers sharing a source_transaction. Each owner's share fits
     * alone here, but together they don't, so the whole invoice is balance-funded.
     */
    @Test
    void whenOwnersTogetherExceedTheCharge_thenNoTransferIsTiedToItEvenThoughEachFitsAlone() {
        givenItems(item(OWNER_A, "10.00"), item(OWNER_B, "10.00")); // 7.00 + 7.00 net
        givenInvoicePaidBy(chargePayment(CHARGE_ID, 1000L)); // $10.00 after a 50% discount
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenOnboarded(OWNER_B, ACCOUNT_B);
        givenNoPriorPayouts();

        List<TransferCreateParams> sent = sentParams();
        assertEquals(2, sent.size());
        assertNull(sent.get(0).getSourceTransaction());
        assertNull(sent.get(1).getSourceTransaction());
    }

    /** A 100% coupon: nothing was charged, so there is no charge to tie a transfer to. */
    @Test
    void whenInvoiceWasForZero_thenOwnersArePaidFromTheBalance() {
        givenItems(item(OWNER_A, "10.00"));
        givenInvoicePaidBy();
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        assertNull(sentParams().get(0).getSourceTransaction());
    }

    /** Subscription invoices are paid through a PaymentIntent; its latest charge is the source. */
    @Test
    void whenInvoiceWasPaidThroughAPaymentIntent_thenItsLatestChargeIsTheSource() {
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_test");
        intent.setLatestCharge("ch_from_intent");
        InvoicePayment.Payment payment = new InvoicePayment.Payment();
        payment.setType("payment_intent");
        payment.setPaymentIntentObject(intent);
        InvoicePayment invoicePayment = new InvoicePayment();
        invoicePayment.setAmountPaid(1000L);
        invoicePayment.setPayment(payment);

        givenItems(item(OWNER_A, "10.00"));
        givenInvoicePaidBy(invoicePayment);
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        assertEquals("ch_from_intent", sentParams().get(0).getSourceTransaction());
    }

    /** Partial payments: no single charge can fund the transfers, so fall back to the balance. */
    @Test
    void whenInvoiceWasPaidInSeveralPayments_thenOwnersArePaidFromTheBalance() {
        givenItems(item(OWNER_A, "10.00"));
        givenInvoicePaidBy(chargePayment("ch_1", 500L), chargePayment("ch_2", 500L));
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        assertNull(sentParams().get(0).getSourceTransaction());
    }

    /** A paid invoice payment we can't resolve to a charge (e.g. paid out of band). */
    @Test
    void whenThePaymentHasNoCharge_thenOwnersArePaidFromTheBalance() {
        InvoicePayment.Payment payment = new InvoicePayment.Payment();
        payment.setType("payment_record");
        InvoicePayment outOfBand = new InvoicePayment();
        outOfBand.setAmountPaid(1000L);
        outOfBand.setPayment(payment);

        givenItems(item(OWNER_A, "10.00"));
        givenInvoicePaidBy(outOfBand);
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        assertNull(sentParams().get(0).getSourceTransaction());
    }

    /**
     * Guessing the funding source would be worse than failing: the balance path fails in live
     * mode. Nothing has moved yet, so the webhook can safely 500 and be redelivered.
     */
    @Test
    void whenTheChargeCannotBeLookedUp_thenItThrowsBeforeAnyTransferOrLedgerRow() {
        givenItems(item(OWNER_A, "10.00"));
        givenNoPriorPayouts();
        invoicePayments.when(() -> InvoicePayment.list(any(InvoicePaymentListParams.class)))
                .thenThrow(new ApiConnectionException("Stripe unreachable"));

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            assertThrows(PayoutFundingLookupException.class,
                    () -> service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID));
            transfers.verifyNoInteractions();
        }
        verify(payoutRepository, never()).save(any());
    }

    /** A fully handled redelivery costs no Stripe call at all. */
    @Test
    void whenEveryOwnerWasAlreadyHandled_thenTheChargeIsNotEvenLookedUp() {
        givenItems(item(OWNER_A, "10.00"));
        when(payoutRepository.existsByStripeInvoiceIdAndMediaOwnerBusinessId(INVOICE_ID, OWNER_A))
                .thenReturn(true);

        service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

        invoicePayments.verifyNoInteractions();
    }

    @Test
    void whenOwnerAlreadyPaidForThisInvoice_thenNoSecondTransfer() {
        givenItems(item(OWNER_A, "4.00"));
        when(payoutRepository.existsByStripeInvoiceIdAndMediaOwnerBusinessId(INVOICE_ID, OWNER_A))
                .thenReturn(true);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verifyNoInteractions();
            verify(payoutRepository, never()).save(any());
        }
    }

    /** A redelivery after a partial run must top up only the owners still unpaid. */
    @Test
    void whenOnlySomeOwnersWerePaid_thenRedeliveryPaysOnlyTheRemainder() {
        givenItems(item(OWNER_A, "4.00"), item(OWNER_B, "10.00"));
        when(payoutRepository.existsByStripeInvoiceIdAndMediaOwnerBusinessId(INVOICE_ID, OWNER_A))
                .thenReturn(true);
        when(payoutRepository.existsByStripeInvoiceIdAndMediaOwnerBusinessId(INVOICE_ID, OWNER_B))
                .thenReturn(false);
        givenOnboarded(OWNER_B, ACCOUNT_B);

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_b"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)));
            assertEquals(ACCOUNT_B, params.getValue().getDestination());
            assertEquals(700L, params.getValue().getAmount());
        }
    }

    @Test
    void whenOwnerHasNoStripeAccount_thenTheirShareIsHeldAndOtherOwnersStillGetPaid() {
        givenItems(item(OWNER_A, "4.00"), item(OWNER_B, "10.00"));
        when(stripeAccountRepository.findByBusinessId(OWNER_A)).thenReturn(Optional.empty());
        givenOnboarded(OWNER_B, ACCOUNT_B);
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_b"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)));
            assertEquals(ACCOUNT_B, params.getValue().getDestination());
        }

        BundlePayout held = savedPayoutFor(OWNER_A);
        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, held.getStatus());
        assertEquals(0, new BigDecimal("2.80").compareTo(held.getAmount()));
        assertEquals(CHARGE_ID, held.getStripeSourceChargeId(), "the release must use the same funding");
        assertNull(held.getLastAttemptAt(), "no transfer was attempted");
        assertNull(held.getStripeTransferId());
        assertEquals(BundlePayoutStatus.PAID, savedPayoutFor(OWNER_B).getStatus());
    }

    @Test
    void whenOwnerOnboardingIncomplete_thenTheirShareIsHeld() {
        givenItems(item(OWNER_A, "4.00"));
        StripeAccount notFinished = new StripeAccount();
        notFinished.setBusinessId(OWNER_A);
        notFinished.setStripeAccountId(ACCOUNT_A);
        notFinished.setOnboardingComplete(false);
        when(stripeAccountRepository.findByBusinessId(OWNER_A)).thenReturn(Optional.of(notFinished));
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);
            transfers.verifyNoInteractions();
        }

        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, savedPayoutFor(OWNER_A).getStatus());
    }

    /**
     * Details are submitted before Stripe has verified the owner. A transfer then would fail for
     * good, so the share is held until charges and payouts are enabled too.
     */
    @Test
    void whenOwnerSubmittedDetailsButIsNotYetVerified_thenTheirShareIsHeldNotAttempted() {
        givenItems(item(OWNER_A, "4.00"));
        StripeAccount pending = new StripeAccount();
        pending.setBusinessId(OWNER_A);
        pending.setStripeAccountId(ACCOUNT_A);
        pending.setOnboardingComplete(true);
        when(stripeAccountRepository.findByBusinessId(OWNER_A)).thenReturn(Optional.of(pending));
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);
            transfers.verifyNoInteractions();
        }

        assertEquals(BundlePayoutStatus.HELD_NOT_ONBOARDED, savedPayoutFor(OWNER_A).getStatus());
    }

    /** media.price is nullable and coalesces to zero (D13); Stripe rejects zero transfers. */
    @Test
    void whenOwnerShareIsZero_thenNoTransferIsAttempted() {
        givenItems(item(OWNER_A, "0.00"));
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);
            transfers.verifyNoInteractions();
        }

        assertEquals(BundlePayoutStatus.SKIPPED_ZERO_AMOUNT, savedPayoutFor(OWNER_A).getStatus());
        verify(stripeAccountRepository, never()).findByBusinessId(anyString());
    }

    /**
     * A rejected transfer must not fail the webhook: Stripe would redeliver an event
     * whose other transfers have already moved money.
     */
    @Test
    void whenStripeRejectsOneTransfer_thenItIsRecordedAsFailedAndTheOthersStillGetPaid() {
        givenItems(item(OWNER_A, "4.00"), item(OWNER_B, "10.00"));
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenOnboarded(OWNER_B, ACCOUNT_B);
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new InvalidRequestException(
                            "Insufficient funds", null, null, "balance_insufficient", 400, null))
                    .thenReturn(givenTransfer("tr_b"));

            assertDoesNotThrow(() -> service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID));
        }

        BundlePayout failed = savedPayoutFor(OWNER_A);
        assertEquals(BundlePayoutStatus.FAILED, failed.getStatus());
        assertTrue(failed.getFailureReason().startsWith("Insufficient funds"));
        assertEquals("balance_insufficient", failed.getFailureCode());
        assertEquals(CHARGE_ID, failed.getStripeSourceChargeId(), "kept so a retry can repeat it exactly");
        assertNotNull(failed.getLastAttemptAt());
        assertNull(failed.getStripeTransferId());
        assertEquals(BundlePayoutStatus.PAID, savedPayoutFor(OWNER_B).getStatus());
    }

    @Test
    void whenSubscriptionHasNoItems_thenNothingIsTransferred() {
        when(itemRepository.findAllBySubscriptionId(SUBSCRIPTION_ID)).thenReturn(List.of());

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);
            transfers.verifyNoInteractions();
        }
        verify(payoutRepository, never()).save(any());
    }

    /** Half-up to the cent, matching the money convention used everywhere else. */
    @Test
    void whenShareDoesNotDivideEvenly_thenItRoundsHalfUpToTheCent() {
        givenItems(item(OWNER_A, "3.33"));
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_1"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)));
            // 3.33 * 0.70 = 2.331 -> 2.33
            assertEquals(233L, params.getValue().getAmount());
        }
    }

    @Test
    void whenTransferSucceeds_thenTheLedgerRecordsGrossNetAndTransferId() {
        givenItems(item(OWNER_A, "4.00"), item(OWNER_A, "6.50"));
        givenOnboarded(OWNER_A, ACCOUNT_A);
        givenNoPriorPayouts();

        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_ledger"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);
        }

        BundlePayout saved = savedPayoutFor(OWNER_A);
        assertEquals(INVOICE_ID, saved.getStripeInvoiceId());
        assertEquals(SUBSCRIPTION_ID, saved.getSubscriptionId());
        assertEquals(0, new BigDecimal("10.50").compareTo(saved.getGrossAmount()));
        assertEquals(0, new BigDecimal("7.35").compareTo(saved.getAmount()));
        assertEquals("tr_ledger", saved.getStripeTransferId());
        assertEquals(BundlePayoutStatus.PAID, saved.getStatus());
    }

    /** Called from a webhook and a status read, neither of which may fail over a payout. */
    @Test
    void whenReleasingHeldPayoutsFailsUnexpectedly_thenTheCallerIsNotAffected() {
        when(payoutRepository.findAllByMediaOwnerBusinessIdAndStatusOrderByCreatedAtAsc(
                OWNER_A, BundlePayoutStatus.HELD_NOT_ONBOARDED)).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> service.releaseHeldPayouts(OWNER_A));
    }

    @Test
    void whenNothingIsHeld_thenTheSweepDoesNothing() {
        when(payoutRepository.findAllByStatusOrderByCreatedAtAsc(BundlePayoutStatus.HELD_NOT_ONBOARDED))
                .thenReturn(List.of());

        service.releaseOrExpireHeldPayouts();

        verifyNoInteractions(stripeAccountRepository);
        verify(payoutRepository, never()).save(any());
    }

    @Test
    void whenTheOwnerHasNoHeldShares_thenReleasingDoesNotLookUpTheirAccount() {
        when(payoutRepository.findAllByMediaOwnerBusinessIdAndStatusOrderByCreatedAtAsc(
                OWNER_A, BundlePayoutStatus.HELD_NOT_ONBOARDED)).thenReturn(List.of());

        service.releaseHeldPayouts(OWNER_A);

        verifyNoInteractions(stripeAccountRepository);
    }

    // --- helpers -----------------------------------------------------------

    /** Pays the invoice out with every Stripe transfer succeeding, and returns what was sent. */
    private List<TransferCreateParams> sentParams() {
        try (MockedStatic<Transfer> transfers = mockStatic(Transfer.class)) {
            ArgumentCaptor<TransferCreateParams> params = ArgumentCaptor.forClass(TransferCreateParams.class);
            transfers.when(() -> Transfer.create(any(TransferCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenTransfer("tr_1"));

            service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID);

            transfers.verify(() -> Transfer.create(params.capture(), any(RequestOptions.class)), atLeastOnce());
            return params.getAllValues();
        }
    }

    private void givenInvoicePaidBy(InvoicePayment... payments) {
        InvoicePaymentCollection collection = new InvoicePaymentCollection();
        collection.setData(new ArrayList<>(List.of(payments)));
        invoicePayments.when(() -> InvoicePayment.list(any(InvoicePaymentListParams.class)))
                .thenReturn(collection);
    }

    private static InvoicePayment chargePayment(String chargeId, long amountPaidInCents) {
        InvoicePayment.Payment payment = new InvoicePayment.Payment();
        payment.setType("charge");
        payment.setCharge(chargeId);
        InvoicePayment invoicePayment = new InvoicePayment();
        invoicePayment.setAmountPaid(amountPaidInCents);
        invoicePayment.setPayment(payment);
        return invoicePayment;
    }

    private void givenItems(BundleSubscriptionItem... items) {
        when(itemRepository.findAllBySubscriptionId(SUBSCRIPTION_ID)).thenReturn(List.of(items));
    }

    private void givenNoPriorPayouts() {
        lenient().when(payoutRepository.existsByStripeInvoiceIdAndMediaOwnerBusinessId(anyString(), anyString()))
                .thenReturn(false);
    }

    private void givenOnboarded(String businessId, String stripeAccountId) {
        StripeAccount account = new StripeAccount();
        account.setBusinessId(businessId);
        account.setStripeAccountId(stripeAccountId);
        account.setOnboardingComplete(true);
        account.setChargesEnabled(true);
        account.setPayoutsEnabled(true);
        lenient().when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.of(account));
    }

    private BundleSubscriptionItem item(String ownerBusinessId, String amount) {
        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(SUBSCRIPTION_ID);
        item.setMediaId(UUID.randomUUID());
        item.setMediaOwnerBusinessId(ownerBusinessId);
        item.setMonthlyAmount(new BigDecimal(amount));
        return item;
    }

    private Transfer givenTransfer(String id) {
        Transfer transfer = new Transfer();
        transfer.setId(id);
        return transfer;
    }

    private BundlePayout savedPayoutFor(String ownerBusinessId) {
        ArgumentCaptor<BundlePayout> captor = ArgumentCaptor.forClass(BundlePayout.class);
        verify(payoutRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(p -> ownerBusinessId.equals(p.getMediaOwnerBusinessId()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No payout recorded for " + ownerBusinessId));
    }
}
