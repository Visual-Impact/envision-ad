package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.TransferCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
 */
@ExtendWith(MockitoExtension.class)
class BundlePayoutServiceUnitTest {

    private static final String INVOICE_ID = "in_test_123";
    private static final String SUBSCRIPTION_ID = "sub-local-abc";
    private static final String OWNER_A = "owner-aaa";
    private static final String OWNER_B = "owner-bbb";
    private static final String ACCOUNT_A = "acct_AAA";
    private static final String ACCOUNT_B = "acct_BBB";

    private BundlePayoutServiceImpl service;

    @Mock private BundleSubscriptionItemRepository itemRepository;
    @Mock private StripeAccountRepository stripeAccountRepository;
    @Mock private BundlePayoutRepository payoutRepository;

    @BeforeEach
    void setUp() {
        service = new BundlePayoutServiceImpl(itemRepository, stripeAccountRepository, payoutRepository);
        ReflectionTestUtils.setField(service, "platformFeePercent", 30);
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
     * collected. Accepted rather than capped — and the reason transfers are made from
     * the platform balance instead of against the invoice's charge, which Stripe caps
     * at the charge amount.
     */
    @Test
    void whenDiscountExceedsPlatformFee_thenPayoutStillExceedsWhatWasCollectedAndIsNotCapped() {
        givenItems(item(OWNER_A, "60.00")); // charged, say, $30.00 after a 50% discount
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
    void whenOwnerHasNoStripeAccount_thenTheyAreSkippedAndOtherOwnersStillGetPaid() {
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

        assertEquals(BundlePayoutStatus.SKIPPED_NOT_ONBOARDED, savedPayoutFor(OWNER_A).getStatus());
        assertEquals(BundlePayoutStatus.PAID, savedPayoutFor(OWNER_B).getStatus());
    }

    @Test
    void whenOwnerOnboardingIncomplete_thenTheyAreSkipped() {
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

        assertEquals(BundlePayoutStatus.SKIPPED_NOT_ONBOARDED, savedPayoutFor(OWNER_A).getStatus());
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
                            "Insufficient funds", null, null, null, 400, null))
                    .thenReturn(givenTransfer("tr_b"));

            assertDoesNotThrow(() -> service.payOutInvoice(INVOICE_ID, SUBSCRIPTION_ID));
        }

        BundlePayout failed = savedPayoutFor(OWNER_A);
        assertEquals(BundlePayoutStatus.FAILED, failed.getStatus());
        assertEquals("Insufficient funds", failed.getFailureReason());
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

    // --- helpers -----------------------------------------------------------

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
