package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.advertisement.exceptions.AdCampaignNotFoundException;
import com.envisionad.webservice.advertisement.exceptions.CampaignHasNoAdsException;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePriceQuote;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePricingService;
import com.envisionad.webservice.bundle.businesslogiclayer.BundleService;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.exceptions.BundleNoEligibleMediaException;
import com.envisionad.webservice.bundle.exceptions.BundleNotActiveException;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.payment.exceptions.DuplicateBundleSubscriptionException;
import com.envisionad.webservice.utils.JwtUtils;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final BundleService bundleService;
    private final BundlePricingService pricingService;
    private final AdCampaignRepository adCampaignRepository;
    private final BusinessRepository businessRepository;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final BundleSubscriptionItemRepository bundleSubscriptionItemRepository;
    private final JwtUtils jwtUtils;

    public BundleSubscriptionServiceImpl(BundleService bundleService,
            BundlePricingService pricingService,
            AdCampaignRepository adCampaignRepository,
            BusinessRepository businessRepository,
            StripeCustomerRepository stripeCustomerRepository,
            BundleSubscriptionRepository bundleSubscriptionRepository,
            BundleSubscriptionItemRepository bundleSubscriptionItemRepository,
            JwtUtils jwtUtils) {
        this.bundleService = bundleService;
        this.pricingService = pricingService;
        this.adCampaignRepository = adCampaignRepository;
        this.businessRepository = businessRepository;
        this.stripeCustomerRepository = stripeCustomerRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.bundleSubscriptionItemRepository = bundleSubscriptionItemRepository;
        this.jwtUtils = jwtUtils;
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
        BundlePriceQuote quote = pricingService.quote(bundleId, businessId);
        if (quote.eligibleMedias().isEmpty() || quote.finalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BundleNoEligibleMediaException(bundleId);
        }

        Optional<BundleSubscription> retryOf = bundleSubscriptionRepository
                .findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                        bundleId, businessId, Set.of(BundleSubscriptionStatus.INCOMPLETE));

        // Settled before the session is created so it can ride along in the subscription
        // metadata, giving M5's invoice.paid a second way to resolve this row.
        String subscriptionId = retryOf
                .map(BundleSubscription::getSubscriptionId)
                .orElseGet(() -> UUID.randomUUID().toString());

        String stripeCustomerId = ensureStripeCustomer(businessId);

        BigDecimal monthlyAmount = quote.finalPrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        Session session = createSubscriptionSession(
                bundle, monthlyAmount, stripeCustomerId, subscriptionId, businessId);

        BundleSubscription subscription = retryOf.orElseGet(BundleSubscription::new);
        subscription.setSubscriptionId(subscriptionId);
        subscription.setBundleId(bundleId);
        subscription.setAdvertiserBusinessId(businessId);
        subscription.setCampaignId(campaign.getCampaignId().getCampaignId());
        subscription.setStripeCheckoutSessionId(session.getId());
        subscription.setStatus(BundleSubscriptionStatus.INCOMPLETE);
        subscription.setMonthlyAmount(monthlyAmount);
        subscription.setScreenCount(quote.eligibleMedias().size());
        bundleSubscriptionRepository.save(subscription);

        writeItems(subscriptionId, quote.eligibleMedias(), retryOf.isPresent());

        log.info("Bundle subscription checkout created: subscription={}, bundle={}, business={}, "
                        + "amount={}, screens={}, session={}{}",
                subscriptionId, bundleId, businessId, monthlyAmount,
                quote.eligibleMedias().size(), session.getId(),
                retryOf.isPresent() ? " (retry of an abandoned checkout)" : "");

        return new SubscriptionCheckoutResult(session.getClientSecret(), session.getId(), subscriptionId);
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
            String stripeCustomerId, String subscriptionId, String businessId) throws StripeException {

        long amountInCents = monthlyAmount.multiply(BigDecimal.valueOf(100)).longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
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
                                .build())
                .build();

        // Timestamped so an abandoned attempt can be retried with a fresh session, matching
        // the legacy reservation checkout's key strategy.
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(subscriptionId + "-subscription-" + System.currentTimeMillis())
                .build();

        return Session.create(params, requestOptions);
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
}
