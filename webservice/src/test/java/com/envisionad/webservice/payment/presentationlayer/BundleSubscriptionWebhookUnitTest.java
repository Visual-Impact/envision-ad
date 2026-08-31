package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.BundlePayoutService;
import com.envisionad.webservice.payment.businesslogiclayer.BundleSubscriptionService;
import com.envisionad.webservice.payment.businesslogiclayer.StripeWebhookService;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionNotLinkedException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The five bundle-subscription webhook handlers.
 *
 * <p>Written against mocked Stripe event objects rather than the integration harness,
 * because these handlers are reached only through Stripe deliveries and the
 * integration harness cannot stub Stripe. The payout service is mocked here; its own
 * arithmetic is covered by {@code BundlePayoutServiceUnitTest}.
 */
@ExtendWith(MockitoExtension.class)
class BundleSubscriptionWebhookUnitTest {

    private static final String LOCAL_SUB_ID = "9510a689-local";
    private static final String STRIPE_SUB_ID = "sub_stripe_123";
    private static final String SESSION_ID = "cs_test_session";
    private static final String INVOICE_ID = "in_test_456";
    private static final long PERIOD_END_EPOCH = 1787706419L;

    private StripeWebhookService service;

    @Mock private StripeAccountRepository stripeAccountRepository;
    @Mock private BundleSubscriptionRepository bundleSubscriptionRepository;
    @Mock private BundlePayoutService bundlePayoutService;
    @Mock private BundleSubscriptionService bundleSubscriptionService;

    @BeforeEach
    void setUp() {
        service = new StripeWebhookService(
                stripeAccountRepository, bundleSubscriptionRepository, bundlePayoutService,
                bundleSubscriptionService);
    }

    // --- checkout.session.completed (mode=subscription) ---------------------

    @Test
    void whenSubscriptionCheckoutCompletes_thenSubscriptionIsLinkedAndActivated() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.INCOMPLETE, null);
        when(bundleSubscriptionRepository.findByStripeCheckoutSessionId(SESSION_ID))
                .thenReturn(Optional.of(row));

        service.handleCheckoutSessionCompleted(eventOf(subscriptionSession()));

        BundleSubscription saved = savedSubscription();
        assertEquals(STRIPE_SUB_ID, saved.getStripeSubscriptionId());
        assertEquals(BundleSubscriptionStatus.ACTIVE, saved.getStatus());
        verify(bundleSubscriptionService).notifyMediaOwnersOfNewSubscription(LOCAL_SUB_ID);
    }

    /**
     * The normal case per the class comment on {@code handleSubscriptionCheckoutCompleted}:
     * invoice.paid usually wins the race and has already activated the row, so this handler's
     * own transition is CANCELED->CANCELED or ACTIVE->ACTIVE, neither of which is a first
     * activation.
     */
    @Test
    void whenCheckoutCompletesAfterInvoicePaidAlreadyActivatedIt_thenNoDuplicateNotification() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.ACTIVE, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findByStripeCheckoutSessionId(SESSION_ID))
                .thenReturn(Optional.of(row));

        service.handleCheckoutSessionCompleted(eventOf(subscriptionSession()));

        verify(bundleSubscriptionService, never()).notifyMediaOwnersOfNewSubscription(anyString());
    }

    /**
     * A replayed pre-M6 {@code mode=payment} checkout must be ignored outright.
     * <p>
     * The one-time reservation payment flow is gone, but the Stripe account still holds its
     * historical events and they can be resent. Falling through to the subscription handler
     * would fail to resolve the session and throw, which tells Stripe to retry an event that
     * can never succeed — so the handler must no-op instead.
     */
    @Test
    void whenLegacyPaymentModeCheckoutIsReplayed_thenItIsIgnoredEntirely() {
        Session session = new Session();
        session.setId(SESSION_ID);
        session.setMode("payment");

        service.handleCheckoutSessionCompleted(eventOf(session));

        verifyNoInteractions(bundleSubscriptionRepository);
        verifyNoInteractions(bundlePayoutService);
    }

    @Test
    void whenSubscriptionCheckoutCompletesForUnknownSession_thenNoOp() {
        when(bundleSubscriptionRepository.findByStripeCheckoutSessionId(SESSION_ID))
                .thenReturn(Optional.empty());

        service.handleCheckoutSessionCompleted(eventOf(subscriptionSession()));

        verify(bundleSubscriptionRepository, never()).save(any());
    }

    @Test
    void whenSubscriptionCheckoutCompletesForCanceledRow_thenItIsNotResurrected() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.CANCELED, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findByStripeCheckoutSessionId(SESSION_ID))
                .thenReturn(Optional.of(row));

        service.handleCheckoutSessionCompleted(eventOf(subscriptionSession()));

        verify(bundleSubscriptionRepository, never()).save(any());
    }

    // --- invoice.paid -------------------------------------------------------

    /**
     * The ordering hazard, resolved on first delivery. invoice.paid reliably arrives
     * before checkout.session.completed, so the row is still unlinked — but the
     * subscription metadata carries our own id, so it resolves anyway and this handler
     * links Stripe's id itself.
     */
    @Test
    void whenInvoicePaidArrivesBeforeCheckoutCompleted_thenItResolvesByMetadataAndLinksTheRow() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.INCOMPLETE, null);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        service.handleInvoicePaid(eventOf(invoiceWith(LOCAL_SUB_ID, STRIPE_SUB_ID)));

        BundleSubscription saved = savedSubscription();
        assertEquals(STRIPE_SUB_ID, saved.getStripeSubscriptionId());
        assertEquals(BundleSubscriptionStatus.ACTIVE, saved.getStatus());
        verify(bundlePayoutService).payOutInvoice(INVOICE_ID, LOCAL_SUB_ID);
        verify(bundleSubscriptionService).notifyMediaOwnersOfNewSubscription(LOCAL_SUB_ID);
    }

    /** A renewal payment must not re-send the "you're now part of this bundle" email. */
    @Test
    void whenInvoiceHasNoMetadata_thenItFallsBackToTheStripeSubscriptionId() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.ACTIVE, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findByStripeSubscriptionId(STRIPE_SUB_ID))
                .thenReturn(Optional.of(row));

        service.handleInvoicePaid(eventOf(invoiceWith(null, STRIPE_SUB_ID)));

        verify(bundlePayoutService).payOutInvoice(INVOICE_ID, LOCAL_SUB_ID);
        verify(bundleSubscriptionRepository, never()).findBySubscriptionId(anyString());
        verify(bundleSubscriptionService, never()).notifyMediaOwnersOfNewSubscription(anyString());
    }

    /** A PAST_DUE recovery payment is a reactivation, not a new subscription — no resend. */
    @Test
    void whenInvoicePaidRecoversAPastDueSubscription_thenNoNewSubscriptionEmailIsSent() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.PAST_DUE, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        service.handleInvoicePaid(eventOf(invoiceWith(LOCAL_SUB_ID, STRIPE_SUB_ID)));

        assertEquals(BundleSubscriptionStatus.ACTIVE, savedSubscription().getStatus());
        verify(bundleSubscriptionService, never()).notifyMediaOwnersOfNewSubscription(anyString());
    }

    /**
     * A notification failure must never turn a successful activation and payout into a
     * failed, Stripe-retried webhook — StripeWebhookService catches around the call for
     * exactly this reason.
     */
    @Test
    void whenNotifyingMediaOwnersThrows_thenTheWebhookStillSucceeds() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.INCOMPLETE, null);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));
        doThrow(new RuntimeException("boom"))
                .when(bundleSubscriptionService).notifyMediaOwnersOfNewSubscription(LOCAL_SUB_ID);

        assertDoesNotThrow(() -> service.handleInvoicePaid(eventOf(invoiceWith(LOCAL_SUB_ID, STRIPE_SUB_ID))));

        verify(bundlePayoutService).payOutInvoice(INVOICE_ID, LOCAL_SUB_ID);
        assertEquals(BundleSubscriptionStatus.ACTIVE, savedSubscription().getStatus());
    }

    /**
     * The pinned behaviour: a lookup miss must throw so WebhookController returns 500
     * and Stripe redelivers. Logging and no-opping would silently drop the payout.
     */
    @Test
    void whenInvoicePaidCannotResolveAnySubscription_thenItThrowsSoStripeRetries() {
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.empty());
        when(bundleSubscriptionRepository.findByStripeSubscriptionId(STRIPE_SUB_ID))
                .thenReturn(Optional.empty());

        assertThrows(BundleSubscriptionNotLinkedException.class,
                () -> service.handleInvoicePaid(eventOf(invoiceWith(LOCAL_SUB_ID, STRIPE_SUB_ID))));

        verifyNoInteractions(bundlePayoutService);
    }

    /** An unreadable payload loses a payout just as surely as an unresolvable one. */
    @Test
    void whenInvoicePaidCannotBeDeserialized_thenItThrowsRatherThanNoOpping() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.handleInvoicePaid(event));
        verifyNoInteractions(bundlePayoutService);
    }

    /**
     * An abandoned checkout completed after the fact mints a second Stripe
     * subscription carrying the same local id. Paying it would pay the owners twice
     * for one bundle; throwing would retry-storm every cycle forever.
     */
    @Test
    void whenInvoiceBelongsToAnOrphanedDuplicateSubscription_thenNoPayoutAndNoThrow() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.ACTIVE, "sub_the_real_one");
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        assertDoesNotThrow(() ->
                service.handleInvoicePaid(eventOf(invoiceWith(LOCAL_SUB_ID, "sub_the_orphan"))));

        verifyNoInteractions(bundlePayoutService);
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    @Test
    void whenInvoicePaidArrivesForCanceledSubscription_thenItIsNotResurrectedAndNotPaidOut() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.CANCELED, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        service.handleInvoicePaid(eventOf(invoiceWith(LOCAL_SUB_ID, STRIPE_SUB_ID)));

        verifyNoInteractions(bundlePayoutService);
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    /**
     * Read from the line item, not invoice.period_end — on a real first invoice both
     * invoice-level period fields equal the creation timestamp.
     */
    @Test
    void whenInvoicePaid_thenRenewalDateComesFromTheLineItemPeriod() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.ACTIVE, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        service.handleInvoicePaid(eventOf(invoiceWith(LOCAL_SUB_ID, STRIPE_SUB_ID)));

        assertEquals(expectedPeriodEnd(), savedSubscription().getCurrentPeriodEnd());
    }

    @Test
    void whenInvoiceIsNotForASubscription_thenItIsIgnored() {
        Invoice invoice = new Invoice();
        invoice.setId(INVOICE_ID);

        service.handleInvoicePaid(eventOf(invoice));

        verifyNoInteractions(bundlePayoutService, bundleSubscriptionRepository);
    }

    /** A payload of the wrong type is as unusable as one that will not deserialize. */
    @Test
    void whenInvoicePaidCarriesTheWrongObjectType_thenItThrows() {
        assertThrows(IllegalStateException.class,
                () -> service.handleInvoicePaid(eventOf(subscriptionSession())));
        verifyNoInteractions(bundlePayoutService);
    }

    /** Non-payout-critical handlers log and give up instead. */
    @Test
    void whenSubscriptionDeletedCarriesTheWrongObjectType_thenItNoOps() {
        assertDoesNotThrow(() -> service.handleSubscriptionDeleted(eventOf(new Invoice())));
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    @Test
    void whenSubscriptionDeletedCannotBeDeserialized_thenItNoOps() {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.handleSubscriptionDeleted(event));
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    /**
     * The scenario that made this guard necessary: tidying up an orphaned duplicate in
     * Stripe emits customer.subscription.deleted carrying the LIVE subscription's own
     * subscriptionId metadata. Without the guard, cleaning up a duplicate would cancel
     * the paying subscription it was duplicating.
     */
    @Test
    void whenAnOrphanedDuplicateIsDeletedInStripe_thenTheLiveSubscriptionIsNotCanceled() {
        BundleSubscription live = givenSubscription(BundleSubscriptionStatus.ACTIVE, "sub_the_live_one");
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(live));

        Subscription orphan = stripeSubscription(false);
        orphan.setId("sub_the_orphan");

        service.handleSubscriptionDeleted(eventOf(orphan));

        verify(bundleSubscriptionRepository, never()).save(any());
        assertEquals(BundleSubscriptionStatus.ACTIVE, live.getStatus());
        assertNull(live.getCanceledAt());
    }

    @Test
    void whenAnOrphanedDuplicateFailsPayment_thenTheLiveSubscriptionIsNotMarkedPastDue() {
        BundleSubscription live = givenSubscription(BundleSubscriptionStatus.ACTIVE, "sub_the_live_one");
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(live));

        service.handleInvoicePaymentFailed(eventOf(invoiceWith(LOCAL_SUB_ID, "sub_the_orphan")));

        verify(bundleSubscriptionRepository, never()).save(any());
        assertEquals(BundleSubscriptionStatus.ACTIVE, live.getStatus());
    }

    @Test
    void whenAnOrphanedDuplicateIsUpdated_thenTheLiveSubscriptionsRenewalDateIsNotRewritten() {
        BundleSubscription live = givenSubscription(BundleSubscriptionStatus.ACTIVE, "sub_the_live_one");
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(live));

        Subscription orphan = stripeSubscription(true);
        orphan.setId("sub_the_orphan");

        service.handleSubscriptionUpdated(eventOf(orphan));

        verify(bundleSubscriptionRepository, never()).save(any());
        assertFalse(live.isCancelAtPeriodEnd());
        assertNull(live.getCurrentPeriodEnd());
    }

    @Test
    void whenStripeSubscriptionIsUpdatedForACanceledRow_thenItIsLeftAlone() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.CANCELED, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        service.handleSubscriptionUpdated(eventOf(stripeSubscription(true)));

        verify(bundleSubscriptionRepository, never()).save(any());
    }

    // --- invoice.payment_failed --------------------------------------------

    @Test
    void whenInvoicePaymentFails_thenSubscriptionGoesPastDue() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.ACTIVE, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        service.handleInvoicePaymentFailed(eventOf(invoiceWith(LOCAL_SUB_ID, STRIPE_SUB_ID)));

        assertEquals(BundleSubscriptionStatus.PAST_DUE, savedSubscription().getStatus());
    }

    @Test
    void whenInvoicePaymentFailsForUnknownSubscription_thenNoOp() {
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.empty());
        when(bundleSubscriptionRepository.findByStripeSubscriptionId(STRIPE_SUB_ID))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.handleInvoicePaymentFailed(eventOf(invoiceWith(LOCAL_SUB_ID, STRIPE_SUB_ID))));

        verify(bundleSubscriptionRepository, never()).save(any());
    }

    // --- customer.subscription.deleted / updated ----------------------------

    @Test
    void whenStripeSubscriptionIsDeleted_thenLocalRowIsCanceled() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.ACTIVE, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        service.handleSubscriptionDeleted(eventOf(stripeSubscription(false)));

        BundleSubscription saved = savedSubscription();
        assertEquals(BundleSubscriptionStatus.CANCELED, saved.getStatus());
        assertNotNull(saved.getCanceledAt());
    }

    @Test
    void whenStripeSubscriptionIsUpdated_thenCancelFlagAndPeriodEndAreSynced() {
        BundleSubscription row = givenSubscription(BundleSubscriptionStatus.ACTIVE, STRIPE_SUB_ID);
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.of(row));

        service.handleSubscriptionUpdated(eventOf(stripeSubscription(true)));

        BundleSubscription saved = savedSubscription();
        assertTrue(saved.isCancelAtPeriodEnd());
        assertEquals(expectedPeriodEnd(), saved.getCurrentPeriodEnd());
        assertEquals(BundleSubscriptionStatus.ACTIVE, saved.getStatus(), "an update must not change status");
    }

    @Test
    void whenUnknownStripeSubscriptionIsUpdated_thenNoOp() {
        when(bundleSubscriptionRepository.findBySubscriptionId(LOCAL_SUB_ID)).thenReturn(Optional.empty());
        when(bundleSubscriptionRepository.findByStripeSubscriptionId(STRIPE_SUB_ID))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.handleSubscriptionUpdated(eventOf(stripeSubscription(true))));

        verify(bundleSubscriptionRepository, never()).save(any());
    }

    // --- helpers -----------------------------------------------------------

    private Event eventOf(StripeObject object) {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(object));
        return event;
    }

    private Session subscriptionSession() {
        Session session = new Session();
        session.setId(SESSION_ID);
        session.setMode("subscription");
        session.setSubscription(STRIPE_SUB_ID);
        return session;
    }

    /** stripe-java 31.x hangs the subscription off parent.subscription_details. */
    private Invoice invoiceWith(String localSubscriptionId, String stripeSubscriptionId) {
        Invoice.Parent.SubscriptionDetails details = new Invoice.Parent.SubscriptionDetails();
        details.setSubscription(stripeSubscriptionId);
        if (localSubscriptionId != null) {
            details.setMetadata(Map.of("subscriptionId", localSubscriptionId));
        }

        Invoice.Parent parent = new Invoice.Parent();
        parent.setType("subscription_details");
        parent.setSubscriptionDetails(details);

        InvoiceLineItem.Period period = new InvoiceLineItem.Period();
        period.setEnd(PERIOD_END_EPOCH);
        InvoiceLineItem line = new InvoiceLineItem();
        line.setPeriod(period);
        InvoiceLineItemCollection lines = new InvoiceLineItemCollection();
        lines.setData(List.of(line));

        Invoice invoice = new Invoice();
        invoice.setId(INVOICE_ID);
        invoice.setParent(parent);
        invoice.setLines(lines);
        return invoice;
    }

    /** current_period_end moved off the Subscription and onto its items in 31.x. */
    private Subscription stripeSubscription(boolean cancelAtPeriodEnd) {
        SubscriptionItem item = new SubscriptionItem();
        item.setCurrentPeriodEnd(PERIOD_END_EPOCH);
        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));

        Subscription subscription = new Subscription();
        subscription.setId(STRIPE_SUB_ID);
        subscription.setItems(items);
        subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        subscription.setMetadata(Map.of("subscriptionId", LOCAL_SUB_ID));
        return subscription;
    }

    private BundleSubscription givenSubscription(BundleSubscriptionStatus status, String stripeSubscriptionId) {
        BundleSubscription row = new BundleSubscription();
        row.setSubscriptionId(LOCAL_SUB_ID);
        row.setBundleId("bundle-1");
        row.setAdvertiserBusinessId("biz-1");
        row.setStripeCheckoutSessionId(SESSION_ID);
        row.setStripeSubscriptionId(stripeSubscriptionId);
        row.setStatus(status);
        row.setMonthlyAmount(new BigDecimal("48.00"));
        row.setScreenCount(15);
        return row;
    }

    private BundleSubscription savedSubscription() {
        ArgumentCaptor<BundleSubscription> captor = ArgumentCaptor.forClass(BundleSubscription.class);
        verify(bundleSubscriptionRepository).save(captor.capture());
        return captor.getValue();
    }

    private LocalDateTime expectedPeriodEnd() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(PERIOD_END_EPOCH), ZoneId.systemDefault());
    }
}
