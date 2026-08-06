package com.envisionad.webservice.payment.presentationlayer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Carries the same {@code clientSecret}/{@code sessionId} JSON the legacy checkout
 * endpoint returns, so the frontend's EmbeddedCheckout wiring is reused verbatim;
 * {@code subscriptionId} is the local row's public id, for correlation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleSubscriptionCheckoutResponseModel {
    private String clientSecret;
    private String sessionId;
    private String subscriptionId;
}
