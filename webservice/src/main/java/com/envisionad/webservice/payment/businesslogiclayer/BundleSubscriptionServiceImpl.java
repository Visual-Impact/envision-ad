package com.envisionad.webservice.payment.businesslogiclayer;

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
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.exceptions.MediaNotFoundException;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.utils.EmailService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class BundleSubscriptionServiceImpl implements BundleSubscriptionService {

    /** A live subscription: blocks a second subscription to the same bundle. */
    private static final Set<BundleSubscriptionStatus> LIVE_STATUSES =
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
    private final EmployeeRepository employeeRepository;
    private final Auth0Service auth0Service;
    private final EmailService emailService;
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
            EmployeeRepository employeeRepository,
            Auth0Service auth0Service,
            EmailService emailService,
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
        this.employeeRepository = employeeRepository;
        this.auth0Service = auth0Service;
        this.emailService = emailService;
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

        Bundle bundle = bundleService.getBundleByBundleId(bundleId);
        if (!bundle.isActive()) {
            throw new BundleNotActiveException(bundleId);
        }

        AdCampaign campaign = validateCampaign(campaignId, businessId);

        // A live subscription blocks a second one. An INCOMPLETE row does not — that is an
        // abandoned checkout, and it is reused below rather than rejected, otherwise the
        // partial unique index would lock the buyer out of this bundle permanently.
        if (bundleSubscriptionRepository
                .findByBundleIdAndAdvertiserBusinessIdAndStatusIn(bundleId, businessId, LIVE_STATUSES)
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
        subscription.setCampaignId(campaign.getCampaignId().getCampaignId());
        subscription.setStripeCheckoutSessionId(session.getId());
        subscription.setStatus(BundleSubscriptionStatus.INCOMPLETE);
        subscription.setMonthlyAmount(monthlyAmount);
        subscription.setScreenCount(quote.eligibleMedias().size());
        // Overwritten on a retry same as every other locked field (D22): a second attempt's
        // coupon (present, absent, or different) always wins over the abandoned one's.
        subscription.setCouponId(coupon != null ? coupon.getId() : null);
        bundleSubscriptionRepository.save(subscription);

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

        return subscriptions.stream()
                .sorted(Comparator.comparing(BundleSubscription::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(subscription -> {
                    Bundle bundle = bundleRepository.findByBundleId(subscription.getBundleId()).orElse(null);
                    AdCampaign campaign =
                            adCampaignRepository.findByCampaignId_CampaignId(subscription.getCampaignId());
                    return responseMapper.entityToResponseModel(
                            subscription, bundle, campaign == null ? null : campaign.getName());
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
                mediaUuid, LIVE_STATUSES);

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

        AdCampaign campaign = adCampaignRepository.findByCampaignId_CampaignId(subscription.getCampaignId());
        // The ads collection is LAZY; this must run inside the caller's transaction to initialize.
        if (campaign == null || campaign.getAds() == null || campaign.getAds().isEmpty()) {
            log.warn("Skipping new-subscription notification for {}: campaign {} has no creatives",
                    subscriptionId, subscription.getCampaignId());
            return;
        }

        Business advertiserBusiness =
                businessRepository.findByBusinessId_BusinessId(subscription.getAdvertiserBusinessId());
        String advertiserName =
                advertiserBusiness != null ? advertiserBusiness.getName() : subscription.getAdvertiserBusinessId();

        String subject = "New creatives for your Envision Ad screens — " + advertiserName;
        String body = buildNewSubscriptionEmailBody(advertiserName, campaign);

        List<String> ownerBusinessIds = bundleSubscriptionItemRepository.findAllBySubscriptionId(subscriptionId)
                .stream()
                .map(BundleSubscriptionItem::getMediaOwnerBusinessId)
                .distinct()
                .toList();

        for (String ownerBusinessId : ownerBusinessIds) {
            try {
                Optional<String> ownerEmail = resolveOwnerEmail(ownerBusinessId);
                if (ownerEmail.isEmpty()) {
                    log.warn("Skipping new-subscription notification for owner {}: no resolvable email",
                            ownerBusinessId);
                    continue;
                }
                emailService.sendSimpleEmail(ownerEmail.get(), subject, body);
            } catch (Exception e) {
                // A mail-server hiccup or a lookup failure for one owner must not stop the
                // others, and must never propagate into the caller's webhook transaction.
                log.error("Failed to notify media owner {} of new subscription {}",
                        ownerBusinessId, subscriptionId, e);
            }
        }
    }

    private String buildNewSubscriptionEmailBody(String advertiserName, AdCampaign campaign) {
        StringBuilder body = new StringBuilder();
        body.append("Hi there,\n\n");
        body.append(advertiserName)
                .append(" just subscribed to a bundle that includes one or more of your screens.\n\n");
        body.append("Campaign: ").append(campaign.getName()).append("\n\n");
        body.append("Please update your display(s) with the following creatives:\n\n");
        for (Ad ad : campaign.getAds()) {
            body.append("- ").append(ad.getName()).append(": ").append(ad.getAdUrl()).append("\n");
        }
        body.append("\nThanks for partnering with Envision Ad!\n");
        body.append("— The Envision Ad Team");
        return body.toString();
    }

    /** Mirrors ProofOfDisplayService's advertiser-email resolution, business-side. */
    private Optional<String> resolveOwnerEmail(String ownerBusinessId) {
        return employeeRepository.findAllByBusinessId_BusinessId(ownerBusinessId).stream()
                .map(Employee::getUserId)
                .filter(uid -> uid != null && !uid.isBlank())
                .findFirst()
                .map(auth0Service::getUserEmailByUserId);
    }
}
