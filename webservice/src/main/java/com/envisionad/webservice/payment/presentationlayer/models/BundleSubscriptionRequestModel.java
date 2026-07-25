package com.envisionad.webservice.payment.presentationlayer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Note the absence of any price field: the amount is recomputed server-side from the
 * pricing pipeline at subscribe time and never taken from the client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleSubscriptionRequestModel {
    private String bundleId;
    private String campaignId;
    private String businessId;
}
