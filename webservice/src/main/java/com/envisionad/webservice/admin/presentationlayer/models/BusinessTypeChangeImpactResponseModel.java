package com.envisionad.webservice.admin.presentationlayer.models;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What setting a business type would mean for an advertiser's existing subscriptions. Shown as a
 * warning in the admin edit modal; it never blocks the change.
 */
@Data
@NoArgsConstructor
public class BusinessTypeChangeImpactResponseModel {
    /**
     * Distinct screens in that venue the advertiser is paying for through live subscriptions.
     * They keep paying for them, but their creatives are no longer sent there.
     */
    private long liveSubscriptionScreenCount;
}
