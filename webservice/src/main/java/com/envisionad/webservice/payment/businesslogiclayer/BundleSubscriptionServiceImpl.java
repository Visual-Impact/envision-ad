package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.activecampaign.businesslogiclayer.ActiveCampaignService;
import com.envisionad.webservice.advertisement.dataaccesslayer.Ad;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.advertisement.exceptions.AdCampaignNotFoundException;
import com.envisionad.webservice.advertisement.exceptions.CampaignHasNoAdsException;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePriceQuote;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePricingService;
import com.envisionad.webservice.bundle.businesslogiclayer.BundleService;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import com.envisionad.webservice.bundle.exceptions.BundleNoEligibleMediaException;
import com.envisionad.webservice.bundle.exceptions.BundleNotActiveException;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.exceptions.BusinessNotVerifiedException;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.exceptions.MediaNotFoundException;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.utils.MediaOwnerNotifier;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionAlreadyPaidException;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionNotFoundException;
import com.envisionad.webservice.payment.exceptions.DuplicateBundleSubscriptionException;
import com.envisionad.webservice.payment.exceptions.InvalidCouponException;
import com.envisionad.webservice.payment.mappinglayer.BundleSubscriptionResponseMapper;
import com.envisionad.webservice.payment.presentationlayer.models.BundleSubscriptionResponseModel;
import com.envisionad.webservice.payment.presentationlayer.models.LiveCampaignResponseModel;
import com.envisionad.webservice.utils.JwtUtils;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BundleSubscriptionServiceImpl implements BundleSubscriptionService {

    /**
     * Which states block a *second* subscription to the same bundle. Deliberately its own
     * constant rather than {@link BundleSubscriptionStatus#LIVE}: it answers a different
     * question (duplicate-checkout prevention, backed by the partial unique index) and could
     * legitimately diverge — the index's own definition covers INCOMPLETE, which this set
     * does not. Same values today; different reasons.
     */
    private static final Set<BundleSubscriptionStatus> DUPLICATE_GUARD_STATUSES =
            Set.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE);

    private static final int MONEY_SCALE = 2;

    /** Stripe Checkout Session statuses relevant to retiring an abandoned attempt. */
    private static final String COMPLETE_SESSION_STATUS = "complete";
    private static final String EXPIRED_SESSION_STATUS = "expired";

    private final BundleService bundleService;
    private final BundlePricingService pricingService;
    private final AdCampaignRepository adCampaignRepository;
    private final BusinessRepository businessRepository;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final BundleSubscriptionItemRepository bundleSubscriptionItemRepository;
    private final BundleRepository bundleRepository;
    private final MediaRepository mediaRepository;
    private final BundleSubscriptionResponseMapper responseMapper;
    private final JwtUtils jwtUtils;
    private final MediaOwnerNotifier mediaOwnerNotifier;
    private final ActiveCampaignService activeCampaignService;
    private final CouponRepository couponRepository;

    public BundleSubscriptionServiceImpl(BundleService bundleService,
            BundlePricingService pricingService,
            AdCampaignRepository adCampaignRepository,
            BusinessRepository businessRepository,
            StripeCustomerRepository stripeCustomerRepository,
            BundleSubscriptionRepository bundleSubscriptionRepository,
            BundleSubscriptionItemRepository bundleSubscriptionItemRepository,
            BundleRepository bundleRepository,
            MediaRepository mediaRepository,
            BundleSubscriptionResponseMapper responseMapper,
            JwtUtils jwtUtils,
            MediaOwnerNotifier mediaOwnerNotifier,
            ActiveCampaignService activeCampaignService,
            CouponRepository couponRepository) {
        this.bundleService = bundleService;
        this.pricingService = pricingService;
        this.adCampaignRepository = adCampaignRepository;
        this.businessRepository = businessRepository;
        this.stripeCustomerRepository = stripeCustomerRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.bundleSubscriptionItemRepository = bundleSubscriptionItemRepository;
        this.bundleRepository = bundleRepository;
        this.mediaRepository = mediaRepository;
        this.responseMapper = responseMapper;
        this.jwtUtils = jwtUtils;
        this.mediaOwnerNotifier = mediaOwnerNotifier;
        this.activeCampaignService = activeCampaignService;
        this.couponRepository = couponRepository;
    }

    /**
     * Every validation runs before the first Stripe call, so a rejected subscribe attempt
     * never leaves an orphaned Customer or Session behind — and so the guard paths stay
     * testable without mocking Stripe.
     */
    @Transactional
    @Override
    public SubscriptionCheckoutResult createSubscriptionCheckout(
            Jwt jwt, String bundleId, String campaignId, String businessId) throws StripeException {
        return createSubscriptionCheckout(jwt, bundleId, campaignId, businessId, null);
    }

    @Transactional
    @Override
    public SubscriptionCheckoutResult createSubscriptionCheckout(
            Jwt jwt, String bundleId, String campaignId, String businessId, String couponCode)
            throws StripeException {

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null || !business.isVerified()) {
            throw new BusinessNotVerifiedException(businessId);
        }

        Bundle bundle = bundleService.getBundleByBundleId(bundleId);
        if (!bundle.isActive()) {
            throw new BundleNotActiveException(bundleId);
        }

        AdCampaign campaign = validateCampaign(campaignId, businessId);

        // A live subscription blocks a second one. An INCOMPLETE row does not — that is an
        // abandoned checkout, and it is reused below rather than rejected, otherwise the
        // partial unique index would lock the buyer out of this bundle permanently.
        if (bundleSubscriptionRepository
                .findByBundleIdAndAdvertiserBusinessIdAndStatusIn(bundleId, businessId, DUPLICATE_GUARD_STATUSES)
                .isPresent()) {
            throw new DuplicateBundleSubscriptionException(bundleId, businessId);
        }

        // Never trust a client-supplied price: the quote is recomputed here even though the
        // modal already previewed it through GET /bundles/{id}/quote.
        BundlePriceQuote quote = pricingService.quote(bundle, businessId);
        if (quote.eligibleMedias().isEmpty() || quote.finalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BundleNoEligibleMediaException(bundleId);
        }

        // A code that doesn't resolve locally is rejected before any Stripe call — there is
        // no stripePromotionCodeId to reference. A code that resolves but is inactive/expired/
        // exhausted is still forwarded to Stripe, which is the actual enforcer (brief §4.6.5,
        // §4.7.2); Stripe rejecting it at session creation is caught below.
        Coupon coupon = null;
        if (couponCode != null && !couponCode.isBlank()) {
            coupon = couponRepository.findByCodeIgnoreCase(couponCode.trim().toUpperCase())
                    .orElseThrow(() -> new InvalidCouponException(
                            "invalid", "Coupon code " + couponCode + " is not valid."));
        }

        Optional<BundleSubscription> retryOf = bundleSubscriptionRepository
                .findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                        bundleId, businessId, Set.of(BundleSubscriptionStatus.INCOMPLETE));

        // Settled before the session is created so it can ride along in the subscription
        // metadata, giving M5's invoice.paid a second way to resolve this row.
        String subscriptionId = retryOf
                .map(BundleSubscription::getSubscriptionId)
                .orElseGet(() -> UUID.randomUUID().toString());

        // Retiring the abandoned attempt's session BEFORE opening a new one is what
        // stops it being completed later and minting a Stripe subscription this
        // platform has no row for. Live data proved this is not hypothetical: three
        // separate Stripe subscriptions ended up carrying the same local
        // subscriptionId, billing one customer three times over. (Decision D38.)
        if (retryOf.isPresent()) {
            retirePreviousSession(retryOf.get().getStripeCheckoutSessionId(), bundleId);
        }

        String stripeCustomerId = ensureStripeCustomer(businessId);

        BigDecimal monthlyAmount = quote.finalPrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        Session session = createSubscriptionSession(
                bundle, monthlyAmount, stripeCustomerId, subscriptionId, businessId, coupon);

        BundleSubscription subscription = retryOf.orElseGet(BundleSubscription::new);
        subscription.setSubscriptionId(subscriptionId);
        subscription.setBundleId(bundleId);
        subscription.setAdvertiserBusinessId(businessId);
        subscription.setStripeCheckoutSessionId(session.getId());
        subscription.setStatus(BundleSubscriptionStatus.INCOMPLETE);
        subscription.setMonthlyAmount(monthlyAmount);
        subscription.setScreenCount(quote.eligibleMedias().size());
        // Overwritten on a retry same as every other locked field (D22): a second attempt's
        // coupon (present, absent, or different) always wins over the abandoned one's.
        subscription.setCouponId(coupon != null ? coupon.getId() : null);
        bundleSubscriptionRepository.save(subscription);

        // FR-6.1: the first subscription must leave a campaign on screen, so checkout makes the
        // choice through the same endpoint the dashboard uses rather than writing the pointer
        // itself. The null guard is load-bearing and stays: selectInitialActiveCampaign refuses
        // outright when a pointer already exists, and the pointer is deliberately sticky
        // (FR-6.3), so calling it unconditionally would fail checkout for every advertiser who
        // has ever subscribed before. An abandoned INCOMPLETE checkout can also leave the pointer
        // set with no live subscription — a legitimate sticky state, since every reader that
        // matters is gated on a live subscription.
        //
        // Known gap, owned by P1's checkout UI and not fixed here: because of that stickiness, a
        // returning advertiser's picker selection has no effect. Making the picker honest is a
        // frontend change, shipping with the rest of the P6 dashboard in M3.
        if (business.getActiveCampaignId() == null) {
            activeCampaignService.selectInitialActiveCampaign(
                    businessId, campaign.getCampaignId().getCampaignId());
        }

        writeItems(subscriptionId, quote.eligibleMedias(), retryOf.isPresent());

        log.info("Bundle subscription checkout created: subscription={}, bundle={}, business={}, "
                        + "amount={}, screens={}, session={}{}",
                subscriptionId, bundleId, businessId, monthlyAmount,
                quote.eligibleMedias().size(), session.getId(),
                retryOf.isPresent() ? " (retry of an abandoned checkout)" : "");

        return new SubscriptionCheckoutResult(session.getClientSecret(), session.getId(), subscriptionId);
    }

    /**
     * Guards run before the Stripe call, same as the subscribe path — both so a
     * rejected cancel never touches Stripe, and so the guard paths stay testable
     * without stubbing it.
     */
    @Transactional
    @Override
    public void cancelSubscription(Jwt jwt, String subscriptionId) throws StripeException {

        BundleSubscription subscription = bundleSubscriptionRepository
                .findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BundleSubscriptionNotFoundException(subscriptionId));

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, subscription.getAdvertiserBusinessId());

        if (subscription.getStatus() == BundleSubscriptionStatus.CANCELED) {
            // Idempotent by design: a double-click should not produce an error.
            log.info("Subscription {} is already canceled — nothing to do", subscriptionId);
            return;
        }

        if (subscription.getStripeSubscriptionId() == null) {
            // An abandoned checkout that never completed. There is nothing at Stripe to
            // cancel, and leaving the row INCOMPLETE would keep occupying this buyer's
            // one-live-subscription-per-bundle slot, so it is closed out locally.
            subscription.setStatus(BundleSubscriptionStatus.CANCELED);
            subscription.setCanceledAt(LocalDateTime.now());
            bundleSubscriptionRepository.save(subscription);
            log.info("Canceled incomplete subscription {} locally — it has no Stripe subscription",
                    subscriptionId);
            return;
        }

        Subscription stripeSubscription = Subscription.retrieve(subscription.getStripeSubscriptionId());
        stripeSubscription.update(
                SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build());

        // Mirrored locally straight away rather than waiting for the
        // customer.subscription.updated webhook, so the advertiser sees the change on
        // their next page load. The webhook still lands and re-syncs the same value.
        subscription.setCancelAtPeriodEnd(true);
        bundleSubscriptionRepository.save(subscription);

        log.info("Subscription {} ({}) set to cancel at period end {}",
                subscriptionId, subscription.getStripeSubscriptionId(), subscription.getCurrentPeriodEnd());
    }

    private AdCampaign validateCampaign(String campaignId, String businessId) {
        AdCampaign campaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (campaign == null) {
            throw new AdCampaignNotFoundException(campaignId);
        }
        jwtUtils.validateBusinessOwnsCampaign(businessId, campaign);
        // The ads collection is LAZY; this runs inside the transaction so it initializes.
        if (campaign.getAds() == null || campaign.getAds().isEmpty()) {
            throw new CampaignHasNoAdsException(campaignId);
        }
        return campaign;
    }

    /**
     * Closes out the checkout session of an abandoned attempt so it can never be
     * completed after the fact.
     *
     * <p>If it turns out the buyer already completed it, the retry is refused rather
     * than expired — they have paid, and opening a second session would charge them
     * twice for one bundle. Their row is activated by the webhook that is already in
     * flight (or by its redelivery).
     *
     * <p>Note this is the one Stripe call that does not sit behind all the pure
     * guards: it can only run in the retry branch, which by definition requires an
     * existing row to have been read first.
     */
    private void retirePreviousSession(String previousSessionId, String bundleId) throws StripeException {
        Session previous;
        try {
            previous = Session.retrieve(previousSessionId);
        } catch (StripeException e) {
            // A session Stripe no longer knows about cannot be completed either, so
            // there is nothing left to protect against.
            log.warn("Could not retrieve previous checkout session {} while retrying: {}",
                    previousSessionId, e.getMessage());
            return;
        }

        if (previous == null) {
            return;
        }

        if (COMPLETE_SESSION_STATUS.equals(previous.getStatus())) {
            log.warn("Refusing retry for bundle {}: session {} was already completed and paid; "
                            + "its activating webhook has not landed yet",
                    bundleId, previousSessionId);
            throw new BundleSubscriptionAlreadyPaidException(bundleId);
        }

        if (EXPIRED_SESSION_STATUS.equals(previous.getStatus())) {
            return;
        }

        previous.expire();
        log.info("Expired abandoned checkout session {} before opening a replacement", previousSessionId);
    }

    /**
     * Stripe Billing needs a Customer, which {@code stripe_accounts} (media-owner Connect
     * accounts) does not provide. The idempotency key is stable per business — unlike the
     * session key below — so a rollback after a successful create cannot strand a
     * duplicate Customer in Stripe on the next attempt.
     */
    private String ensureStripeCustomer(String businessId) throws StripeException {
        Optional<StripeCustomer> existing = stripeCustomerRepository.findByBusinessId(businessId);
        if (existing.isPresent()) {
            return existing.get().getStripeCustomerId();
        }

        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        CustomerCreateParams params = CustomerCreateParams.builder()
                // Business carries no email column, so the Customer is identified by name
                // plus the metadata back-reference.
                .setName(business != null ? business.getName() : businessId)
                .putMetadata("businessId", businessId)
                .build();
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("bundle-customer-" + businessId)
                .build();

        Customer customer = Customer.create(params, requestOptions);

        StripeCustomer stripeCustomer = new StripeCustomer();
        stripeCustomer.setBusinessId(businessId);
        stripeCustomer.setStripeCustomerId(customer.getId());
        stripeCustomerRepository.save(stripeCustomer);

        log.info("Created Stripe Customer {} for business {}", customer.getId(), businessId);
        return customer.getId();
    }

    private Session createSubscriptionSession(Bundle bundle, BigDecimal monthlyAmount,
            String stripeCustomerId, String subscriptionId, String businessId, Coupon coupon)
            throws StripeException {

        long amountInCents = monthlyAmount.multiply(BigDecimal.valueOf(100)).longValueExact();

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setUiMode(SessionCreateParams.UiMode.EMBEDDED)
                .setRedirectOnCompletion(SessionCreateParams.RedirectOnCompletion.NEVER)
                .setCustomer(stripeCustomerId)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(Currency.CAD.toString().toLowerCase())
                                                .setUnitAmount(amountInCents)
                                                .setRecurring(
                                                        SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                                                .setInterval(SessionCreateParams.LineItem.PriceData
                                                                        .Recurring.Interval.MONTH)
                                                                .build())
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(bundle.getNameEn())
                                                                .build())
                                                .build())
                                .setQuantity(1L)
                                .build())
                // Deliberately no transfer_data and no application_fee_amount: the platform
                // is merchant of record for the whole charge, and media-owner shares go out
                // as separate Transfers on invoice.paid (M5). A bundle spans many owners,
                // which the single-destination transfer_data mechanism cannot express.
                .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                                .putMetadata("subscriptionId", subscriptionId)
                                .putMetadata("bundleId", bundle.getBundleId())
                                .putMetadata("advertiserBusinessId", businessId)
                                .build());

        // Our own input box (bundle checkout's coupon field) resolves to a promotion_code id
        // passed here programmatically — Stripe's own hosted promo box (allow_promotion_codes)
        // stays off, deliberately, to avoid a second entry point that bypasses the §4.6 preview.
        if (coupon != null) {
            paramsBuilder.addDiscount(SessionCreateParams.Discount.builder()
                    .setPromotionCode(coupon.getStripePromotionCodeId())
                    .build());
        }
        SessionCreateParams params = paramsBuilder.build();

        // Timestamped so an abandoned attempt can be retried with a fresh session, matching
        // the legacy reservation checkout's key strategy.
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(subscriptionId + "-subscription-" + System.currentTimeMillis())
                .build();

        try {
            return Session.create(params, requestOptions);
        } catch (StripeException e) {
            // Stripe validates and redeems the promotion code atomically at session creation
            // (brief §4.7.2) — a rejection here means it was invalid/inactive/expired/exhausted
            // at this exact instant, even though it passed the earlier client-side preview
            // (brief §4.6.5). Translated to InvalidCouponException so the frontend gets the
            // same error vocabulary as /coupons/validate rather than a generic 500. Stripe's
            // own exception doesn't reliably distinguish which of the three reasons applied, so
            // this collapses them to "invalid" rather than guessing.
            if (coupon != null) {
                throw new InvalidCouponException("invalid",
                        "Coupon code " + coupon.getCode() + " was rejected by Stripe: " + e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Freezes the per-owner split. On a retry the previous items are dropped first —
     * eligibility can have drifted since the abandoned attempt, so the old rows are stale
     * rather than mergeable.
     */
    private void writeItems(String subscriptionId, List<Media> eligibleMedias, boolean isRetry) {
        if (isRetry) {
            bundleSubscriptionItemRepository.deleteAllBySubscriptionId(subscriptionId);
        }

        List<BundleSubscriptionItem> items = eligibleMedias.stream().map(media -> {
            BundleSubscriptionItem item = new BundleSubscriptionItem();
            item.setSubscriptionId(subscriptionId);
            item.setMediaId(media.getId());
            item.setMediaOwnerBusinessId(media.getBusinessId().toString());
            item.setMonthlyAmount(media.getPrice() == null
                    ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                    : media.getPrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            return item;
        }).toList();

        bundleSubscriptionItemRepository.saveAll(items);
    }

    @Override
    public List<BundleSubscriptionResponseModel> getSubscriptionsForBusiness(Jwt jwt, String businessId) {
        String userId = jwtUtils.extractUserId(jwt);
        jwtUtils.validateUserIsEmployeeOfBusiness(userId, businessId);

        List<BundleSubscription> subscriptions =
                bundleSubscriptionRepository.findAllByAdvertiserBusinessId(businessId);

        // Since the P6 follow-up, the campaign shown against a subscription is the advertiser's
        // single active campaign (business.active_campaign_id), not a per-row value — so it is
        // resolved once, not per subscription. It is the same campaign for every row and may be
        // null (advertiser has not picked, or has no live subscription).
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        String activeCampaignId = business == null ? null : business.getActiveCampaignId();
        AdCampaign activeCampaign = activeCampaignId == null
                ? null
                : adCampaignRepository.findByCampaignId_CampaignId(activeCampaignId);
        String activeCampaignName = activeCampaign == null ? null : activeCampaign.getName();

        return subscriptions.stream()
                .sorted(Comparator.comparing(BundleSubscription::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(subscription -> {
                    Bundle bundle = bundleRepository.findByBundleId(subscription.getBundleId()).orElse(null);
                    Coupon coupon = subscription.getCouponId() == null
                            ? null
                            : couponRepository.findById(subscription.getCouponId()).orElse(null);
                    return responseMapper.entityToResponseModel(
                            subscription, bundle, activeCampaignId, activeCampaignName, coupon);
                })
                .toList();
    }

    @Override
    public List<LiveCampaignResponseModel> getLiveCampaignsForMedia(Jwt jwt, String mediaId) {
        String userId = jwtUtils.extractUserId(jwt);

        UUID mediaUuid = UUID.fromString(mediaId);
        Media media = mediaRepository.findById(mediaUuid)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));

        if (media.getBusinessId() == null) {
            throw new IllegalStateException("Media has no associated business");
        }
        // Only the screen's owner may see which advertisers are running on it.
        jwtUtils.validateUserIsEmployeeOfBusiness(userId, media.getBusinessId().toString());

        List<String> campaignIds = bundleSubscriptionItemRepository.findLiveCampaignIdsByMediaId(
                mediaUuid, BundleSubscriptionStatus.LIVE);

        return campaignIds.stream()
                .map(campaignId -> {
                    AdCampaign campaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
                    return new LiveCampaignResponseModel(
                            campaignId, campaign == null ? campaignId : campaign.getName());
                })
                .toList();
    }

    @Override
    public void notifyMediaOwnersOfNewSubscription(String subscriptionId) {
        BundleSubscription subscription =
                bundleSubscriptionRepository.findBySubscriptionId(subscriptionId).orElse(null);
        if (subscription == null) {
            log.warn("Cannot notify media owners for subscription {}: row not found", subscriptionId);
            return;
        }

        Business advertiserBusiness =
                businessRepository.findByBusinessId_BusinessId(subscription.getAdvertiserBusinessId());

        // Since the P6 follow-up, the campaign for this notification is the advertiser's single
        // active campaign (business.active_campaign_id), not a value frozen on the subscription.
        String activeCampaignId =
                advertiserBusiness == null ? null : advertiserBusiness.getActiveCampaignId();
        AdCampaign campaign = activeCampaignId == null
                ? null
                : adCampaignRepository.findByCampaignId_CampaignId(activeCampaignId);
        // The ads collection is LAZY; this must run inside the caller's transaction to initialize.
        if (campaign == null || campaign.getAds() == null || campaign.getAds().isEmpty()) {
            log.warn("Skipping new-subscription notification for {}: active campaign {} has no creatives",
                    subscriptionId, activeCampaignId);
            return;
        }

        String advertiserName =
                advertiserBusiness != null ? advertiserBusiness.getName() : subscription.getAdvertiserBusinessId();

        Bundle bundle = bundleRepository.findByBundleId(subscription.getBundleId()).orElse(null);
        String bundleName = bundle != null ? bundle.getNameEn() : subscription.getBundleId();

        String subject = "New creatives for your Envision Ad screens — " + advertiserName;

        List<BundleSubscriptionItem> items =
                bundleSubscriptionItemRepository.findAllBySubscriptionId(subscriptionId);

        Map<String, List<BundleSubscriptionItem>> itemsByOwner = items.stream()
                .collect(Collectors.groupingBy(BundleSubscriptionItem::getMediaOwnerBusinessId));

        List<MediaOwnerNotifier.OwnerMessage> messages = new ArrayList<>();
        for (Map.Entry<String, List<BundleSubscriptionItem>> entry : itemsByOwner.entrySet()) {
            String ownerBusinessId = entry.getKey();
            try {
                List<BundleSubscriptionItem> ownerItems = entry.getValue();
                List<UUID> ownerMediaIds = ownerItems.stream()
                        .map(BundleSubscriptionItem::getMediaId)
                        .toList();
                List<Media> ownerMedias = mediaRepository.findAllByIdWithLocation(ownerMediaIds);
                BigDecimal ownerMonthlyTotal = ownerItems.stream()
                        .map(BundleSubscriptionItem::getMonthlyAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                String body = buildNewSubscriptionEmailBody(
                        advertiserName, bundleName, campaign, ownerMedias, ownerMonthlyTotal);
                messages.add(new MediaOwnerNotifier.OwnerMessage(ownerBusinessId, subject, body));
            } catch (Exception e) {
                // A lookup failure while composing one owner's email must not cost the others
                // theirs, and must never propagate into the caller's webhook transaction.
                log.error("Could not compose the new-subscription notification for media owner {} "
                        + "on subscription {}", ownerBusinessId, subscriptionId, e);
            }
        }

        // Address resolution and delivery — including per-owner failure isolation — live in the
        // notifier, which P6's swap and auto-notify paths share.
        mediaOwnerNotifier.send(messages);
    }

    private String buildNewSubscriptionEmailBody(
            String advertiserName,
            String bundleName,
            AdCampaign campaign,
            List<Media> ownerMedias,
            BigDecimal ownerMonthlyTotal) {
        StringBuilder body = new StringBuilder();
        body.append("Hi there,\n\n");
        body.append(advertiserName)
                .append(" just subscribed to the \"")
                .append(bundleName)
                .append("\" bundle, which includes the following of your screens:\n\n");
        for (Media media : ownerMedias) {
            body.append("- ").append(media.getTitle());
            if (media.getMediaLocation() != null) {
                body.append(" (")
                        .append(media.getMediaLocation().getName())
                        .append(", ")
                        .append(media.getMediaLocation().getCity())
                        .append(")");
            }
            body.append("\n");
        }
        body.append("\nYou'll earn $").append(ownerMonthlyTotal).append("/month from this subscription.\n");
        body.append("\nCampaign: ").append(campaign.getName()).append("\n\n");
        body.append("Please update your display(s) with the following creatives:\n\n");
        for (Ad ad : campaign.getAds()) {
            body.append("- ").append(ad.getName()).append(": ").append(ad.getAdUrl()).append("\n");
        }
        body.append("\nThanks for partnering with Envision Ad!\n");
        body.append("— The Envision Ad Team");
        return body.toString();
    }
}
