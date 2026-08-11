package com.envisionad.webservice.payment.mappinglayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.Coupon;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionResponseModel;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper, matching {@code VenueResponseMapper} and the bundle module rather than
 * MapStruct: the response needs fields from three aggregates that carry no JPA association
 * between them (subscriptions hold plain scalar FK columns — decision D5).
 */
@Component
public class BundleSubscriptionResponseMapper {

    /**
     * @param bundle       the subscribed bundle, or {@code null} if it has since been deleted
     * @param campaignName the campaign's display name, or {@code null} if it cannot be resolved
     * @param coupon       the coupon applied at signup, or {@code null} if none was (P3). Only its
     *                     static terms are surfaced — never a computed "current effective price":
     *                     that would have to independently reconstruct billing-cycle state Stripe
     *                     already owns, with no signal if it ever drifted wrong (user decision,
     *                     2026-08-10, after the "my subscriptions" list showed the locked
     *                     pre-coupon price with no explanation).
     */
    public BundleSubscriptionResponseModel entityToResponseModel(
            BundleSubscription subscription, Bundle bundle, String campaignName, Coupon coupon) {

        BundleSubscriptionResponseModel response = new BundleSubscriptionResponseModel();
        response.setSubscriptionId(subscription.getSubscriptionId());
        response.setBundleId(subscription.getBundleId());

        // A bundle can only be deleted when it has no live subscriptions (brief req. 5), so a
        // missing bundle here means a CANCELED row whose bundle was cleaned up afterwards. That
        // is legitimate history, so the row is still returned — just without a name.
        if (bundle != null) {
            response.setBundleNameEn(bundle.getNameEn());
            response.setBundleNameFr(bundle.getNameFr());
        }

        response.setStatus(subscription.getStatus());
        response.setMonthlyAmount(subscription.getMonthlyAmount());
        response.setScreenCount(subscription.getScreenCount());
        response.setCurrentPeriodEnd(subscription.getCurrentPeriodEnd());
        response.setCancelAtPeriodEnd(subscription.isCancelAtPeriodEnd());
        response.setCanceledAt(subscription.getCanceledAt());
        response.setCreatedAt(subscription.getCreatedAt());
        response.setCampaignId(subscription.getCampaignId());
        response.setCampaignName(campaignName);

        if (coupon != null) {
            response.setCouponCode(coupon.getCode());
            response.setCouponDiscountType(coupon.getDiscountType());
            response.setCouponPercentOff(coupon.getPercentOff());
            response.setCouponAmountOffCents(coupon.getAmountOffCents());
            response.setCouponDuration(coupon.getDuration());
            response.setCouponDurationInMonths(coupon.getDurationInMonths());
        }
        return response;
    }
}
