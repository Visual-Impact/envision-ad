package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebhookController.
 * Tests Stripe webhook handling and event processing.
 */
@ExtendWith(MockitoExtension.class)
class WebhookControllerUnitTest {

    @InjectMocks
    private WebhookController webhookController;

    @Mock
    private StripeWebhookService webhookService;

    private static final String TEST_EVENTS_WEBHOOK_SECRET = "whsec_test_events123";
    private static final String TEST_CONNECT_WEBHOOK_SECRET = "whsec_test_connect123";
    private static final String VALID_SIGNATURE = "valid_signature";
    private static final String INVALID_SIGNATURE = "invalid_signature";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webhookController, "eventsWebhookSecret", TEST_EVENTS_WEBHOOK_SECRET);
        ReflectionTestUtils.setField(webhookController, "connectWebhookSecret", TEST_CONNECT_WEBHOOK_SECRET);
    }

    @Test
    void handleStripeWebhook_shouldProcessCheckoutSessionCompleted() {
        // Given
        String payload = "{\"type\":\"checkout.session.completed\"}";
        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("checkout.session.completed");

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, VALID_SIGNATURE, TEST_EVENTS_WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

            doNothing().when(webhookService).handleCheckoutSessionCompleted(mockEvent);

            // When
            ResponseEntity<String> response = webhookController.handleStripeWebhook(payload, VALID_SIGNATURE);

            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Webhook handled", response.getBody());
            verify(webhookService, times(1)).handleCheckoutSessionCompleted(mockEvent);
        }
    }

    @Test
    void handleStripeWebhook_shouldHandleUnhandledEventType() {
        // Given
        String payload = "{\"type\":\"customer.created\"}";
        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("customer.created");

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, VALID_SIGNATURE, TEST_EVENTS_WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

            // When
            ResponseEntity<String> response = webhookController.handleStripeWebhook(payload, VALID_SIGNATURE);

            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Webhook handled", response.getBody());
            verify(webhookService, never()).handleCheckoutSessionCompleted(any());
        }
    }

    @Test
    void handleStripeWebhook_shouldReturnBadRequest_whenSignatureIsInvalid() {
        // Given
        String payload = "{\"type\":\"checkout.session.completed\"}";

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, INVALID_SIGNATURE, TEST_EVENTS_WEBHOOK_SECRET))
                    .thenThrow(new SignatureVerificationException("Invalid signature", "sig_header"));
            webhookMock.when(() -> Webhook.constructEvent(payload, INVALID_SIGNATURE, TEST_CONNECT_WEBHOOK_SECRET))
                    .thenThrow(new SignatureVerificationException("Invalid signature", "sig_header"));

            // When
            ResponseEntity<String> response = webhookController.handleStripeWebhook(payload, INVALID_SIGNATURE);

            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Invalid signature", response.getBody());
            verify(webhookService, never()).handleCheckoutSessionCompleted(any());
        }
    }

    @Test
    void handleStripeWebhook_shouldReturnInternalServerError_whenProcessingFails() {
        // Given
        String payload = "{\"type\":\"checkout.session.completed\"}";
        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("checkout.session.completed");

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, VALID_SIGNATURE, TEST_EVENTS_WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

            doThrow(new RuntimeException("Database connection failed"))
                    .when(webhookService).handleCheckoutSessionCompleted(mockEvent);

            // When
            ResponseEntity<String> response = webhookController.handleStripeWebhook(payload, VALID_SIGNATURE);

            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("Webhook processing failed", response.getBody());
            verify(webhookService, times(1)).handleCheckoutSessionCompleted(mockEvent);
        }
    }

    @Test
    void handleStripeWebhook_shouldHandleNullPointerException() {
        // Given
        String payload = "{\"type\":\"invoice.paid\"}";
        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("invoice.paid");

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, VALID_SIGNATURE, TEST_EVENTS_WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

            doThrow(new NullPointerException("Subscription not found"))
                    .when(webhookService).handleInvoicePaid(mockEvent);

            // When
            ResponseEntity<String> response = webhookController.handleStripeWebhook(payload, VALID_SIGNATURE);

            // Then
            assertNotNull(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("Webhook processing failed", response.getBody());
        }
    }
}

