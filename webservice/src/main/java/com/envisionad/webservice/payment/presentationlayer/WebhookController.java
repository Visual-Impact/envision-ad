package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final StripeWebhookService webhookService;

    @Value("${stripe.webhook-secret-events}")
    private String eventsWebhookSecret;

    @Value("${stripe.webhook-secret-connect}")
    private String connectWebhookSecret;

    public WebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            event = verifySignature(payload, sigHeader);
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        log.info("Received Stripe webhook event: {}", event.getType());

        try {
            // Handle different event types
            switch (event.getType()) {
                // Both legacy one-time payments and bundle subscriptions arrive here;
                // the service tells them apart by the session's mode.
                case "checkout.session.completed":
                    webhookService.handleCheckoutSessionCompleted(event);
                    break;

                case "invoice.paid":
                    webhookService.handleInvoicePaid(event);
                    break;

                case "invoice.payment_failed":
                    webhookService.handleInvoicePaymentFailed(event);
                    break;

                case "customer.subscription.deleted":
                    webhookService.handleSubscriptionDeleted(event);
                    break;

                case "customer.subscription.updated":
                    webhookService.handleSubscriptionUpdated(event);
                    break;

                case "account.updated":
                    webhookService.handleAccountUpdated(event);
                    break;

                default:
                    log.debug("Unhandled event type: {}", event.getType());
            }

            return ResponseEntity.ok("Webhook handled");

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            // Return 500 to trigger Stripe retry mechanism
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook processing failed");
        }
    }

    // Two Stripe destinations point at this endpoint (account-scoped events, connected-account
    // events), each with its own signing secret — try both before rejecting.
    private Event verifySignature(String payload, String sigHeader) throws SignatureVerificationException {
        try {
            return Webhook.constructEvent(payload, sigHeader, eventsWebhookSecret);
        } catch (SignatureVerificationException eventsFailure) {
            try {
                return Webhook.constructEvent(payload, sigHeader, connectWebhookSecret);
            } catch (SignatureVerificationException connectFailure) {
                log.error("Webhook signature verification failed against both 'events' and 'connect' secrets");
                throw connectFailure;
            }
        }
    }
}