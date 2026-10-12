package com.envisionad.webservice.proofofdisplay.presentationlayer.models;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One row of the media owner's Proof of Display table (P12 FR-3.1): a subscription that covers at
 * least one of the owner's screens.
 * <p>
 * Identified by advertiser + bundle, not by campaign (P12-PROGRESS D1). Since P6 a subscription
 * has no campaign of its own: {@code campaignName} is the advertiser's current active campaign,
 * which can be null and changes on every swap, while the link stays with the subscription.
 */
@Data
public class MediaOwnerProofSubscriptionResponseModel {

    private String subscriptionId;
    private BundleSubscriptionStatus subscriptionStatus;

    private String advertiserBusinessName;

    /** Both locales, like {@code BundleSubscriptionResponseModel}; null if the bundle was deleted. */
    private String bundleNameEn;
    private String bundleNameFr;

    /** The advertiser's active campaign, or null when they have not picked one. */
    private String campaignName;

    /**
     * Titles of this owner's screens the subscription covers. On a multi-owner subscription, only
     * this owner's screens are listed: the others belong to someone else.
     */
    private List<String> screenNames;

    private ProofLinkResponseModel proofLink;

    /** When the link was last saved; null unless {@code proofLink.status} is {@code SET}. */
    private OffsetDateTime linkUpdatedAt;
}
