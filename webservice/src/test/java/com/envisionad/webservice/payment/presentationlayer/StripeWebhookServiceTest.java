package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.BundlePayoutService;
import com.envisionad.webservice.payment.businesslogiclayer.StripeWebhookService;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.stripe.model.Account;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Connect account syncing plus the event-deserialization guards.
 * <p>
 * P1 M6 removed this class's original bulk — the {@code payment_intent.*} handlers,
 * {@code updateReservationStatus} and the reservation notification emails all went with the
 * weekly-reservation system. The subscription handlers that replaced them are covered by
 * {@code BundleSubscriptionWebhookUnitTest}, which replays real Stripe payloads.
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @InjectMocks
    private StripeWebhookService stripeWebhookService;

    @Mock
    private StripeAccountRepository stripeAccountRepository;

    @Mock
    private BundleSubscriptionRepository bundleSubscriptionRepository;

    @Mock
    private BundlePayoutService bundlePayoutService;

    @Mock
    private Event event;

    @Mock
    private EventDataObjectDeserializer deserializer;

    @BeforeEach
    void setUp() {
        reset(stripeAccountRepository, bundleSubscriptionRepository, bundlePayoutService, event, deserializer);
    }

    // ==================== handleCheckoutSessionCompleted guards ====================

    @Test
    void whenHandleCheckoutSessionCompleted_withEmptyDeserializer_thenReturnEarly() {
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.empty());

        stripeWebhookService.handleCheckoutSessionCompleted(event);

        verify(bundleSubscriptionRepository, never()).findByStripeCheckoutSessionId(anyString());
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    @Test
    void whenHandleCheckoutSessionCompleted_withNonSessionObject_thenReturnEarly() {
        StripeObject nonSessionObject = mock(com.stripe.model.PaymentIntent.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(nonSessionObject));

        stripeWebhookService.handleCheckoutSessionCompleted(event);

        verify(bundleSubscriptionRepository, never()).findByStripeCheckoutSessionId(anyString());
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    // ==================== handleAccountUpdated Tests ====================

    @Test
    void whenHandleAccountUpdated_withValidAccountEvent_thenUpdateStripeAccount() {
        // Arrange
        String stripeAccountId = "acct_123Test";
        Account stripeAccount = mock(Account.class);
        when(stripeAccount.getId()).thenReturn(stripeAccountId);
        when(stripeAccount.getDetailsSubmitted()).thenReturn(true);
        when(stripeAccount.getChargesEnabled()).thenReturn(true);
        when(stripeAccount.getPayoutsEnabled()).thenReturn(true);

        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(stripeAccount));

        StripeAccount localStripeAccount = new StripeAccount();
        localStripeAccount.setStripeAccountId(stripeAccountId);
        localStripeAccount.setOnboardingComplete(false); // Initial state
        localStripeAccount.setChargesEnabled(false);
        localStripeAccount.setPayoutsEnabled(false);

        when(stripeAccountRepository.findByStripeAccountId(stripeAccountId)).thenReturn(Optional.of(localStripeAccount));

        // Act
        stripeWebhookService.handleAccountUpdated(event);

        // Assert
        ArgumentCaptor<StripeAccount> accountCaptor = ArgumentCaptor.forClass(StripeAccount.class);
        verify(stripeAccountRepository).save(accountCaptor.capture());

        StripeAccount savedAccount = accountCaptor.getValue();
        assertTrue(savedAccount.isOnboardingComplete());
        assertTrue(savedAccount.isChargesEnabled());
        assertTrue(savedAccount.isPayoutsEnabled());
    }

    @Test
    void whenHandleAccountUpdated_withNoLocalAccount_thenLogWarningAndDoNotSave() {
        // Arrange
        String stripeAccountId = "acct_unknown";
        Account stripeAccount = mock(Account.class);
        when(stripeAccount.getId()).thenReturn(stripeAccountId);

        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(stripeAccount));
        when(stripeAccountRepository.findByStripeAccountId(stripeAccountId)).thenReturn(Optional.empty());

        // Act
        stripeWebhookService.handleAccountUpdated(event);

        // Assert
        verify(stripeAccountRepository, never()).save(any(StripeAccount.class));
    }

    @Test
    void whenHandleAccountUpdated_withEmptyDeserializer_thenReturnEarly() {
        // Arrange
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.empty());

        // Act
        stripeWebhookService.handleAccountUpdated(event);

        // Assert
        verify(stripeAccountRepository, never()).findByStripeAccountId(anyString());
        verify(stripeAccountRepository, never()).save(any(StripeAccount.class));
    }

    @Test
    void whenHandleAccountUpdated_withNonAccountObject_thenReturnEarly() {
        // Arrange
        StripeObject nonAccountObject = mock(Session.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(nonAccountObject));

        // Act
        stripeWebhookService.handleAccountUpdated(event);

        // Assert
        verify(stripeAccountRepository, never()).findByStripeAccountId(anyString());
        verify(stripeAccountRepository, never()).save(any(StripeAccount.class));
    }
}
