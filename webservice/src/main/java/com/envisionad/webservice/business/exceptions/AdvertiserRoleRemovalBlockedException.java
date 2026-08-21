package com.envisionad.webservice.business.exceptions;

/**
 * Thrown when an admin tries to remove the Advertiser role from a business while it
 * holds a live (ACTIVE/PAST_DUE) bundle subscription. Blocks on live commercial
 * commitment, not mere existence — an advertiser with no subscriptions (or only
 * canceled ones) can drop the role freely; a campaign with no subscription behind it
 * is not a billing relationship.
 */
public class AdvertiserRoleRemovalBlockedException extends RuntimeException {
    public AdvertiserRoleRemovalBlockedException(String businessId, long liveSubscriptionCount) {
        super("Business " + businessId + " cannot lose the Advertiser role: " + liveSubscriptionCount
                + " live bundle subscription(s) still bill this business. Cancel them first — "
                + "removing the role does not release or refund an active subscription.");
    }
}
