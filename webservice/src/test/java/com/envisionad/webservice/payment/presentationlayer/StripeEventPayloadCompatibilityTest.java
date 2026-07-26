package com.envisionad.webservice.payment.presentationlayer;

import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the assumption the whole {@code invoice.paid} handler rests on: that a real
 * webhook payload from this Stripe account can actually be deserialized by the pinned
 * SDK, and carries the fields the handler reads.
 *
 * <p>Why this is not paranoia. A webhook event is frozen at the API version in force
 * when it was <em>created</em>, which is not necessarily the version the SDK is built
 * against — the fixture here is {@code 2025-12-15.clover} while {@code stripe-java
 * 31.3.0} pins {@code 2026-01-28.clover}. If that gap ever stops being tolerated,
 * {@code getDataObjectDeserializer().getObject()} starts returning empty, and because
 * {@code handleInvoicePaid} deliberately throws rather than no-ops on a bad payload,
 * every single delivery would 500 and Stripe would retry forever. That failure would
 * be invisible to every other test in the suite, all of which build their Stripe
 * objects in memory rather than from a real payload.
 *
 * <p>The fixture is a genuine event pulled from the sandbox
 * ({@code evt_1TxGWw1H9mg2Am09BU0hbHGb}), with customer-identifying fields nulled.
 */
class StripeEventPayloadCompatibilityTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_for_fixture_verification";

    @Test
    void aRealInvoicePaidPayloadDeserializesAndCarriesTheFieldsTheHandlerReads() throws Exception {
        String payload = readFixture();

        // Through Webhook.constructEvent, exactly as WebhookController does, rather
        // than a bare Gson parse — so signature verification and event construction
        // are both on the tested path.
        Event event = Webhook.constructEvent(payload, signatureHeaderFor(payload), WEBHOOK_SECRET);

        assertEquals("invoice.paid", event.getType());
        assertNotEquals(com.stripe.Stripe.API_VERSION, event.getApiVersion(),
                "fixture is meant to be an older API version than the SDK pins — that is the point");

        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();
        assertTrue(deserialized.isPresent(),
                "an API-version gap must not make the payload undeserializable: handleInvoicePaid "
                        + "throws on an unreadable payload, so this would 500 on every delivery forever");

        assertInstanceOf(Invoice.class, deserialized.get());
        Invoice invoice = (Invoice) deserialized.get();

        // stripe-java 31.x: the subscription hangs off parent.subscription_details,
        // not a flat invoice.subscription.
        assertNotNull(invoice.getParent(), "invoice.parent is how the subscription is reached");
        Invoice.Parent.SubscriptionDetails details = invoice.getParent().getSubscriptionDetails();
        assertNotNull(details);
        assertNotNull(details.getSubscription(), "the Stripe subscription id");
        assertNotNull(details.getMetadata().get("subscriptionId"),
                "our own subscriptionId rides in subscription metadata — the primary lookup key");

        // The renewal date comes from the line's period, not the invoice-level one,
        // which on a first invoice equals the creation timestamp.
        assertNotNull(invoice.getLines());
        assertFalse(invoice.getLines().getData().isEmpty());
        assertNotNull(invoice.getLines().getData().get(0).getPeriod().getEnd());
    }

    /** Stripe's {@code t=<ts>,v1=<hmac>} scheme, signed now so it is inside tolerance. */
    private String signatureHeaderFor(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000L;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hmac);
    }

    private String readFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/stripe/invoice-paid-event.json")) {
            assertNotNull(in, "missing test fixture /stripe/invoice-paid-event.json");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
