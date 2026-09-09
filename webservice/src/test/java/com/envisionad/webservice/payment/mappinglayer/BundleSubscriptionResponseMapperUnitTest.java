package com.envisionad.webservice.payment.mappinglayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.payment.dataaccesslayer.Coupon;
import com.envisionad.webservice.payment.dataaccesslayer.CouponDuration;
import com.envisionad.webservice.payment.dataaccesslayer.DiscountType;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionResponseModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleSubscriptionResponseMapperUnitTest {

    private final BundleSubscriptionResponseMapper mapper = new BundleSubscriptionResponseMapper();

    /**
     * Since the P6 follow-up the campaign is no longer stored on the subscription — the caller
     * resolves the advertiser's active campaign (business.active_campaign_id) once and passes it
     * in. It is identical across every one of the advertiser's rows.
     */
    private static final String ACTIVE_CAMPAIGN_ID = "camp-1";

    private BundleSubscription subscription() {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId("sub-1");
        subscription.setBundleId("bundle-1");
        subscription.setAdvertiserBusinessId("biz-1");
        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        subscription.setMonthlyAmount(new BigDecimal("48.00"));
        subscription.setScreenCount(15);
        subscription.setCreatedAt(LocalDateTime.now().minusDays(3));
        return subscription;
    }

    private Bundle bundle() {
        Bundle bundle = new Bundle();
        bundle.setBundleId("bundle-1");
        bundle.setNameEn("Full Network");
        bundle.setNameFr("Réseau complet");
        return bundle;
    }

    @Test
    void mapsEveryFieldTheAdvertiserListNeeds() {
        BundleSubscription subscription = subscription();
        LocalDateTime renewal = LocalDateTime.now().plusDays(20);
        subscription.setCurrentPeriodEnd(renewal);

        BundleSubscriptionResponseModel response =
                mapper.entityToResponseModel(subscription, bundle(), ACTIVE_CAMPAIGN_ID, "Winter Sale", null);

        assertEquals("sub-1", response.getSubscriptionId());
        assertEquals("bundle-1", response.getBundleId());
        assertEquals("Full Network", response.getBundleNameEn());
        assertEquals("Réseau complet", response.getBundleNameFr());
        assertEquals(BundleSubscriptionStatus.ACTIVE, response.getStatus());
        assertEquals(new BigDecimal("48.00"), response.getMonthlyAmount());
        assertEquals(15, response.getScreenCount());
        assertEquals(renewal, response.getCurrentPeriodEnd());
        assertEquals("camp-1", response.getCampaignId());
        assertEquals("Winter Sale", response.getCampaignName());
    }

    /**
     * NULL renewal date is reachable on an ACTIVE row — only {@code invoice.paid} populates it, so
     * a subscription activated by {@code checkout.session.completed} has none. It must pass
     * through as null rather than being defaulted.
     */
    @Test
    void passesThroughANullRenewalDate() {
        BundleSubscription subscription = subscription();
        subscription.setCurrentPeriodEnd(null);

        BundleSubscriptionResponseModel response =
                mapper.entityToResponseModel(subscription, bundle(), ACTIVE_CAMPAIGN_ID, "Winter Sale", null);

        assertNull(response.getCurrentPeriodEnd());
    }

    @Test
    void carriesCancelAtPeriodEndSoTheUiCanTellItApartFromAPlainActiveRow() {
        BundleSubscription subscription = subscription();
        subscription.setCancelAtPeriodEnd(true);
        LocalDateTime canceledAt = LocalDateTime.now();
        subscription.setCanceledAt(canceledAt);

        BundleSubscriptionResponseModel response =
                mapper.entityToResponseModel(subscription, bundle(), ACTIVE_CAMPAIGN_ID, "Winter Sale", null);

        assertEquals(BundleSubscriptionStatus.ACTIVE, response.getStatus());
        assertTrue(response.isCancelAtPeriodEnd());
        assertEquals(canceledAt, response.getCanceledAt());
    }

    /**
     * A bundle can only be deleted once it has no live subscriptions, so a missing bundle means a
     * CANCELED row whose bundle was tidied up afterwards. That history still has to render.
     */
    @Test
    void stillMapsWhenTheBundleHasSinceBeenDeleted() {
        BundleSubscription subscription = subscription();
        subscription.setStatus(BundleSubscriptionStatus.CANCELED);

        BundleSubscriptionResponseModel response =
                mapper.entityToResponseModel(subscription, null, ACTIVE_CAMPAIGN_ID, "Winter Sale", null);

        assertEquals("sub-1", response.getSubscriptionId());
        assertNull(response.getBundleNameEn());
        assertNull(response.getBundleNameFr());
        assertEquals(BundleSubscriptionStatus.CANCELED, response.getStatus());
    }

    @Test
    void toleratesAnUnresolvableCampaignName() {
        BundleSubscriptionResponseModel response =
                mapper.entityToResponseModel(subscription(), bundle(), ACTIVE_CAMPAIGN_ID, null, null);

        assertEquals("camp-1", response.getCampaignId());
        assertNull(response.getCampaignName());
    }

    @Test
    void mapsCouponTermsWhenACouponWasApplied() {
        Coupon coupon = new Coupon();
        coupon.setCode("WELCOME20");
        coupon.setDiscountType(DiscountType.PERCENT);
        coupon.setPercentOff(new BigDecimal("20.00"));
        coupon.setDuration(CouponDuration.REPEATING);
        coupon.setDurationInMonths(3);

        BundleSubscriptionResponseModel response =
                mapper.entityToResponseModel(subscription(), bundle(), ACTIVE_CAMPAIGN_ID, "Winter Sale", coupon);

        assertEquals("WELCOME20", response.getCouponCode());
        assertEquals(DiscountType.PERCENT, response.getCouponDiscountType());
        assertEquals(new BigDecimal("20.00"), response.getCouponPercentOff());
        assertNull(response.getCouponAmountOffCents());
        assertEquals(CouponDuration.REPEATING, response.getCouponDuration());
        assertEquals(3, response.getCouponDurationInMonths());
    }

    @Test
    void leavesCouponFieldsNullWhenNoCouponWasApplied() {
        BundleSubscriptionResponseModel response =
                mapper.entityToResponseModel(subscription(), bundle(), ACTIVE_CAMPAIGN_ID, "Winter Sale", null);

        assertNull(response.getCouponCode());
        assertNull(response.getCouponDiscountType());
        assertNull(response.getCouponDuration());
    }
}
