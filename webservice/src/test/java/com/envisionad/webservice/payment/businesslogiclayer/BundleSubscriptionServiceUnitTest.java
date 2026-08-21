package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePriceQuote;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePricingService;
import com.envisionad.webservice.bundle.businesslogiclayer.BundleService;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import com.envisionad.webservice.bundle.exceptions.BundleNoEligibleMediaException;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.business.exceptions.BusinessNotVerifiedException;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.payment.mappinglayer.BundleSubscriptionResponseMapper;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionAlreadyPaidException;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionNotFoundException;
import com.envisionad.webservice.payment.exceptions.InvalidCouponException;
import com.envisionad.webservice.utils.EmailService;
import com.envisionad.webservice.utils.JwtUtils;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The Stripe-touching half of the subscribe flow: what we send to Stripe, and what we
 * freeze locally afterwards. Guard paths that never reach Stripe are covered by
 * {@code BundleSubscriptionControllerIntegrationTest} against a real database.
 */
@ExtendWith(MockitoExtension.class)
class BundleSubscriptionServiceUnitTest {

    private static final String BUNDLE_ID = "bundle-abc";
    private static final String BUSINESS_ID = "biz-123";
    private static final String CAMPAIGN_ID = "camp-456";
    private static final String CUSTOMER_ID = "cus_test123";
    private static final String SESSION_ID = "cs_test_session";

    private BundleSubscriptionServiceImpl service;

    @Mock private BundleService bundleService;
    @Mock private BundlePricingService pricingService;
    @Mock private AdCampaignRepository adCampaignRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private StripeCustomerRepository stripeCustomerRepository;
    @Mock private BundleSubscriptionRepository bundleSubscriptionRepository;
    @Mock private BundleSubscriptionItemRepository bundleSubscriptionItemRepository;
    @Mock private BundleRepository bundleRepository;
    @Mock private MediaRepository mediaRepository;
    @Mock private BundleSubscriptionResponseMapper responseMapper;
    @Mock private JwtUtils jwtUtils;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private Auth0Service auth0Service;
    @Mock private EmailService emailService;
    @Mock private CouponRepository couponRepository;

    private Jwt jwt;
    private Media montrealScreen;
    private Media lavalScreen;
    private UUID ownerA;
    private UUID ownerB;

    @BeforeEach
    void setUp() {
        service = new BundleSubscriptionServiceImpl(bundleService, pricingService, adCampaignRepository,
                businessRepository, stripeCustomerRepository, bundleSubscriptionRepository,
                bundleSubscriptionItemRepository, bundleRepository, mediaRepository, responseMapper,
                jwtUtils, employeeRepository, auth0Service, emailService, couponRepository);

        jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "auth0|user").build();

        ownerA = UUID.randomUUID();
        ownerB = UUID.randomUUID();
        montrealScreen = givenMedia(ownerA, "4.00");
        lavalScreen = givenMedia(ownerB, "6.50");
    }

    // ---------- happy path ----------

    @Test
    void createSubscriptionCheckout_sendsASubscriptionModeEmbeddedSessionAndLocksTheSplit() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen, lavalScreen), "10.50");
        when(stripeCustomerRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.empty());

        try (MockedStatic<Customer> customers = mockStatic(Customer.class);
             MockedStatic<Session> sessions = mockStatic(Session.class)) {

            customers.when(() -> Customer.create(any(CustomerCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeCustomer());
            ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            SubscriptionCheckoutResult result = service.createSubscriptionCheckout(
                    jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);

            assertEquals(SESSION_ID, result.sessionId());
            assertEquals("cs_test_session_secret", result.clientSecret());
            assertNotNull(result.subscriptionId());

            sessions.verify(() -> Session.create(params.capture(), any(RequestOptions.class)));
            SessionCreateParams sent = params.getValue();

            assertEquals(SessionCreateParams.Mode.SUBSCRIPTION, sent.getMode());
            assertEquals(SessionCreateParams.UiMode.EMBEDDED, sent.getUiMode());
            assertEquals(SessionCreateParams.RedirectOnCompletion.NEVER, sent.getRedirectOnCompletion());
            assertEquals(CUSTOMER_ID, sent.getCustomer());

            SessionCreateParams.LineItem.PriceData priceData = sent.getLineItems().get(0).getPriceData();
            assertEquals("cad", priceData.getCurrency());
            assertEquals(1050L, priceData.getUnitAmount(), "dollars must be sent to Stripe as cents");
            assertEquals(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH,
                    priceData.getRecurring().getInterval());

            assertEquals(result.subscriptionId(),
                    sent.getSubscriptionData().getMetadata().get("subscriptionId"));
        }
    }

    /**
     * A bundle spans many owners, so the single-destination transfer_data mechanism is
     * wrong here — payouts go out as separate Transfers on invoice.paid instead. Asserted
     * because silently regaining transfer_data would misroute real money.
     */
    @Test
    void createSubscriptionCheckout_neverSetsTransferDataOnTheSession() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        givenExistingStripeCustomer();

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);

            sessions.verify(() -> Session.create(params.capture(), any(RequestOptions.class)));
            assertNull(params.getValue().getPaymentIntentData(),
                    "subscription checkout must carry no payment_intent_data/transfer_data");
        }
    }

    @Test
    void createSubscriptionCheckout_freezesOneItemPerEligibleScreenSummingToTheLockedTotal() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen, lavalScreen), "10.50");
        givenExistingStripeCustomer();

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);
        }

        ArgumentCaptor<BundleSubscription> saved = ArgumentCaptor.forClass(BundleSubscription.class);
        verify(bundleSubscriptionRepository).save(saved.capture());
        assertEquals(BundleSubscriptionStatus.INCOMPLETE, saved.getValue().getStatus());
        assertEquals(new BigDecimal("10.50"), saved.getValue().getMonthlyAmount());
        assertEquals(2, saved.getValue().getScreenCount());
        assertEquals(SESSION_ID, saved.getValue().getStripeCheckoutSessionId());
        assertNull(saved.getValue().getStripeSubscriptionId(),
                "the Stripe subscription id is linked by the webhook, not here");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BundleSubscriptionItem>> items = ArgumentCaptor.forClass(List.class);
        verify(bundleSubscriptionItemRepository).saveAll(items.capture());

        assertEquals(2, items.getValue().size());
        assertEquals(saved.getValue().getMonthlyAmount(),
                items.getValue().stream().map(BundleSubscriptionItem::getMonthlyAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                "the frozen per-owner split must sum to the locked monthly amount");
        assertEquals(Set.of(ownerA.toString(), ownerB.toString()),
                items.getValue().stream().map(BundleSubscriptionItem::getMediaOwnerBusinessId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    // ---------- organization verification ----------

    /**
     * The verification guard runs before the bundle/campaign are even looked up, so
     * this deliberately skips {@code givenValidPreconditions()} — stubbing those would
     * leave them unused and trip strict-stub verification.
     */
    @Test
    void createSubscriptionCheckout_forAnUnverifiedBusiness_throwsBeforeAnyStripeCall() {
        Business unverified = givenBusiness("Acme Coffee");
        unverified.setVerified(false);
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(unverified);

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            assertThrows(BusinessNotVerifiedException.class, () -> service.createSubscriptionCheckout(
                    jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID));
            sessions.verifyNoInteractions();
        }
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    @Test
    void createSubscriptionCheckout_forAnUnknownBusiness_throwsBeforeAnyStripeCall() {
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(null);

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            assertThrows(BusinessNotVerifiedException.class, () -> service.createSubscriptionCheckout(
                    jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID));
            sessions.verifyNoInteractions();
        }
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    // ---------- Stripe Customer ----------

    @Test
    void createSubscriptionCheckout_reusesAnExistingStripeCustomer() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        givenExistingStripeCustomer();

        try (MockedStatic<Customer> customers = mockStatic(Customer.class);
             MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);

            customers.verifyNoInteractions();
        }
        verify(stripeCustomerRepository, never()).save(any());
    }

    /**
     * Stable per business, unlike the session key: if the transaction rolls back after
     * Stripe created the Customer, the next attempt must return that same Customer rather
     * than mint a duplicate.
     */
    @Test
    void createSubscriptionCheckout_createsTheStripeCustomerWithABusinessStableIdempotencyKey() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        when(stripeCustomerRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.empty());

        try (MockedStatic<Customer> customers = mockStatic(Customer.class);
             MockedStatic<Session> sessions = mockStatic(Session.class)) {

            ArgumentCaptor<CustomerCreateParams> params = ArgumentCaptor.forClass(CustomerCreateParams.class);
            ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
            customers.when(() -> Customer.create(any(CustomerCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeCustomer());
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);

            customers.verify(() -> Customer.create(params.capture(), options.capture()));
            assertEquals("Acme Coffee", params.getValue().getName());
            // CustomerCreateParams#getMetadata is declared as Object (it doubles as the
            // extra-params escape hatch), so it needs narrowing before lookup.
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> metadata =
                    (java.util.Map<String, String>) params.getValue().getMetadata();
            assertEquals(BUSINESS_ID, metadata.get("businessId"));
            assertEquals("bundle-customer-" + BUSINESS_ID, options.getValue().getIdempotencyKey());
        }

        verify(stripeCustomerRepository).save(argThat(c -> CUSTOMER_ID.equals(c.getStripeCustomerId())));
    }

    // ---------- abandoned-checkout retry (D22) ----------

    @Test
    void createSubscriptionCheckout_reusesAnIncompleteRowAndRewritesItsItems() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen, lavalScreen), "10.50");
        givenExistingStripeCustomer();

        BundleSubscription abandoned = new BundleSubscription();
        abandoned.setSubscriptionId("existing-sub-id");
        abandoned.setBundleId(BUNDLE_ID);
        abandoned.setAdvertiserBusinessId(BUSINESS_ID);
        abandoned.setCampaignId(CAMPAIGN_ID);
        abandoned.setStripeCheckoutSessionId("cs_test_stale");
        abandoned.setStatus(BundleSubscriptionStatus.INCOMPLETE);
        abandoned.setMonthlyAmount(new BigDecimal("4.00"));
        abandoned.setScreenCount(1);
        // Both lookups need stubbing: with one of them explicit, strict stubs treat the
        // other's arguments as an unexpected call rather than falling back to empty.
        when(bundleSubscriptionRepository.findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                BUNDLE_ID, BUSINESS_ID,
                Set.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE)))
                .thenReturn(Optional.empty());
        when(bundleSubscriptionRepository.findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                BUNDLE_ID, BUSINESS_ID, Set.of(BundleSubscriptionStatus.INCOMPLETE)))
                .thenReturn(Optional.of(abandoned));

        // Built before the static stubbing starts: creating a mock inside a
        // MockedStatic.when(...) argument leaves Mockito mid-stubbing and blows up.
        Session staleSession = givenOpenSession();

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
            // The retry now retires the abandoned session before opening a replacement.
            sessions.when(() -> Session.retrieve("cs_test_stale")).thenReturn(staleSession);
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            SubscriptionCheckoutResult result = service.createSubscriptionCheckout(
                    jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);

            assertEquals("existing-sub-id", result.subscriptionId(),
                    "a retry must keep the original subscription id, not mint a second row");

            sessions.verify(() -> Session.create(params.capture(), any(RequestOptions.class)));
            assertEquals("existing-sub-id",
                    params.getValue().getSubscriptionData().getMetadata().get("subscriptionId"));
        }

        ArgumentCaptor<BundleSubscription> saved = ArgumentCaptor.forClass(BundleSubscription.class);
        verify(bundleSubscriptionRepository).save(saved.capture());
        assertSame(abandoned, saved.getValue(), "the abandoned row is updated, not replaced");
        assertEquals(SESSION_ID, saved.getValue().getStripeCheckoutSessionId(),
                "the stale session id must be overwritten so its late webhook no-ops");
        assertEquals(new BigDecimal("10.50"), saved.getValue().getMonthlyAmount(),
                "the price is re-quoted on retry, not carried over");
        assertEquals(2, saved.getValue().getScreenCount());

        // Eligibility can have drifted since the abandoned attempt, so the old split is
        // stale rather than mergeable.
        verify(bundleSubscriptionItemRepository).deleteAllBySubscriptionId("existing-sub-id");
    }

    /**
     * Re-subscribing after cancelling starts a genuinely new subscription: the CANCELED row
     * is history, is not matched by the INCOMPLETE reuse lookup, and keeps its own frozen
     * items under its own id. Asserting it here because M5's payout reads items by
     * subscription id, so a new signup quietly inheriting or clearing an old one's split
     * would corrupt what gets paid out.
     */
    @Test
    void createSubscriptionCheckout_afterACancellation_startsAFreshRowAndLeavesTheOldSplitAlone()
            throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        givenExistingStripeCustomer();
        // A CANCELED row is invisible to both lookups: the live-status guard and the
        // INCOMPLETE reuse lookup each return empty, which the default mock behaviour gives.

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            SubscriptionCheckoutResult result = service.createSubscriptionCheckout(
                    jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);

            ArgumentCaptor<BundleSubscription> saved = ArgumentCaptor.forClass(BundleSubscription.class);
            verify(bundleSubscriptionRepository).save(saved.capture());
            assertEquals(result.subscriptionId(), saved.getValue().getSubscriptionId());
            assertEquals(BundleSubscriptionStatus.INCOMPLETE, saved.getValue().getStatus());
        }

        // Nothing is deleted, so the canceled subscription's items stay intact.
        verify(bundleSubscriptionItemRepository, never()).deleteAllBySubscriptionId(anyString());
    }

    @Test
    void createSubscriptionCheckout_doesNotClearItemsOnAFirstAttempt() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        givenExistingStripeCustomer();

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);
        }

        verify(bundleSubscriptionItemRepository, never()).deleteAllBySubscriptionId(anyString());
    }

    // ---------- pricing guards ----------

    @Test
    void createSubscriptionCheckout_withAZeroPricedQuote_isRejectedBeforeAnyStripeCall() {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "0.00");

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            assertThrows(BundleNoEligibleMediaException.class,
                    () -> service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID));
            sessions.verifyNoInteractions();
        }
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    @Test
    void createSubscriptionCheckout_withAnEmptyEligibleSet_isRejected() {
        givenValidPreconditions();
        givenQuote(List.of(), "0.00");

        assertThrows(BundleNoEligibleMediaException.class,
                () -> service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID));
    }

    /** A screen with no price still counts toward the screen count, contributing zero. */
    @Test
    void createSubscriptionCheckout_writesZeroForAPricelessScreen() throws Exception {
        givenValidPreconditions();
        Media priceless = givenMedia(ownerB, null);
        givenQuote(List.of(montrealScreen, priceless), "4.00");
        givenExistingStripeCustomer();

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BundleSubscriptionItem>> items = ArgumentCaptor.forClass(List.class);
        verify(bundleSubscriptionItemRepository).saveAll(items.capture());
        assertEquals(2, items.getValue().size());
        assertTrue(items.getValue().stream()
                .anyMatch(i -> i.getMonthlyAmount().compareTo(BigDecimal.ZERO) == 0));
    }

    // ---------- coupons (P3) ----------

    @Test
    void createSubscriptionCheckout_withACouponCode_attachesThePromotionCodeDiscount() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        givenExistingStripeCustomer();
        Coupon coupon = givenCoupon("WELCOME20", "promo_welcome20");
        when(couponRepository.findByCodeIgnoreCase("WELCOME20")).thenReturn(Optional.of(coupon));

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
            sessions.when(() -> Session.create(params.capture(), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID, "welcome20");

            assertEquals(1, params.getValue().getDiscounts().size());
            assertEquals("promo_welcome20", params.getValue().getDiscounts().get(0).getPromotionCode());
        }

        ArgumentCaptor<BundleSubscription> saved = ArgumentCaptor.forClass(BundleSubscription.class);
        verify(bundleSubscriptionRepository).save(saved.capture());
        assertEquals(coupon.getId(), saved.getValue().getCouponId());
    }

    @Test
    void createSubscriptionCheckout_withoutACouponCode_sendsNoDiscountsAndLeavesCouponIdNull() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        givenExistingStripeCustomer();

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
            sessions.when(() -> Session.create(params.capture(), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID, null);

            assertTrue(params.getValue().getDiscounts() == null || params.getValue().getDiscounts().isEmpty());
        }
        verify(couponRepository, never()).findByCodeIgnoreCase(any());

        ArgumentCaptor<BundleSubscription> saved = ArgumentCaptor.forClass(BundleSubscription.class);
        verify(bundleSubscriptionRepository).save(saved.capture());
        assertNull(saved.getValue().getCouponId());
    }

    @Test
    void createSubscriptionCheckout_withAnUnknownCouponCode_throwsInvalidCouponBeforeAnyStripeCall() {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        when(couponRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            assertThrows(InvalidCouponException.class, () -> service.createSubscriptionCheckout(
                    jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID, "NOPE"));
            sessions.verifyNoInteractions();
        }
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    /**
     * Stripe validates and redeems the code atomically at session creation (brief §4.7.2) — a
     * rejection there, even for a code that resolves locally, must surface the same
     * InvalidCouponException as an unknown code, not a raw StripeException (brief §4.6.5).
     */
    @Test
    void createSubscriptionCheckout_whenStripeRejectsTheCoupon_throwsInvalidCouponException() throws Exception {
        givenValidPreconditions();
        givenQuote(List.of(montrealScreen), "4.00");
        givenExistingStripeCustomer();
        Coupon coupon = givenCoupon("EXPIRED10", "promo_expired10");
        when(couponRepository.findByCodeIgnoreCase("EXPIRED10")).thenReturn(Optional.of(coupon));

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(mock(com.stripe.exception.InvalidRequestException.class));

            assertThrows(InvalidCouponException.class, () -> service.createSubscriptionCheckout(
                    jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID, "EXPIRED10"));
        }
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    // ---------- fixtures ----------

    private Coupon givenCoupon(String code, String stripePromotionCodeId) {
        Coupon coupon = new Coupon();
        coupon.setId(42L);
        coupon.setCouponId(UUID.randomUUID().toString());
        coupon.setCode(code);
        coupon.setStripeCouponId("stripe_coupon_" + code);
        coupon.setStripePromotionCodeId(stripePromotionCodeId);
        return coupon;
    }

    private void givenValidPreconditions() {
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(givenBusiness("Acme Coffee"));

        Bundle bundle = new Bundle();
        bundle.setBundleId(BUNDLE_ID);
        bundle.setNameEn("Full Network");
        bundle.setNameFr("Réseau complet");
        bundle.setRuleType(BundleRuleType.FULL_NETWORK);
        bundle.setActive(true);
        when(bundleService.getBundleByBundleId(BUNDLE_ID)).thenReturn(bundle);

        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(CAMPAIGN_ID));
        campaign.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        Ad ad = new Ad();
        ad.setAdIdentifier(new AdIdentifier());
        ad.setAdType(AdType.IMAGE);
        campaign.setAds(List.of(ad));
        when(adCampaignRepository.findByCampaignId_CampaignId(CAMPAIGN_ID)).thenReturn(campaign);
    }

    private void givenQuote(List<Media> eligible, String finalPrice) {
        when(pricingService.quote(any(Bundle.class), eq(BUSINESS_ID)))
                .thenReturn(new BundlePriceQuote(eligible, new BigDecimal(finalPrice), new BigDecimal(finalPrice)));
    }

    private void givenExistingStripeCustomer() {
        StripeCustomer existing = new StripeCustomer();
        existing.setBusinessId(BUSINESS_ID);
        existing.setStripeCustomerId(CUSTOMER_ID);
        when(stripeCustomerRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(existing));
    }

    private Business givenBusiness(String name) {
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        business.setName(name);
        business.setVerified(true);
        return business;
    }

    private Customer givenStripeCustomer() {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        return customer;
    }

    /** An abandoned-but-still-open session, as the retry path expects to find. */
    private Session givenOpenSession() throws StripeException {
        Session session = mock(Session.class);
        when(session.getStatus()).thenReturn("open");
        when(session.expire()).thenReturn(session);
        return session;
    }

    private Session givenStripeSession() {
        Session session = new Session();
        session.setId(SESSION_ID);
        session.setClientSecret(SESSION_ID + "_secret");
        return session;
    }

    private Media givenMedia(UUID ownerBusinessId, String price) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setBusinessId(ownerBusinessId);
        media.setPrice(price == null ? null : new BigDecimal(price));
        return media;
    }

    private Media givenMediaWithLocation(UUID ownerBusinessId, String title, String locationName, String city) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setBusinessId(ownerBusinessId);
        media.setTitle(title);
        com.envisionad.webservice.media.DataAccessLayer.MediaLocation location =
                new com.envisionad.webservice.media.DataAccessLayer.MediaLocation();
        location.setName(locationName);
        location.setCity(city);
        media.setMediaLocation(location);
        return media;
    }

    // ---------- retry: retiring the abandoned session (M5) ----------

    /**
     * The ghost-subscription fix. Leaving the abandoned session live let it be
     * completed after the fact, minting a Stripe subscription with no local row —
     * live data showed three of them sharing one local subscriptionId, billing one
     * customer three times over.
     */
    @Test
    void whenRetryingAnAbandonedCheckout_thenThePreviousSessionIsExpiredFirst() throws StripeException {
        givenValidPreconditions();
        givenQuote(List.of(givenMedia(UUID.randomUUID(), "4.00")), "4.00");
        givenExistingStripeCustomer();
        givenAbandonedAttempt("cs_test_old", "open");

        // A mock rather than a spy: expire() on a real Session would call Stripe.
        Session previous = mock(Session.class);
        when(previous.getStatus()).thenReturn("open");
        when(previous.expire()).thenReturn(previous);

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.retrieve("cs_test_old")).thenReturn(previous);
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(givenStripeSession());

            assertDoesNotThrow(() -> service.createSubscriptionCheckout(
                    jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID));

            verify(previous).expire();
        }
    }

    /**
     * If the "abandoned" session was in fact completed, the buyer has already paid and
     * their activating webhook simply has not landed. Opening a second session would
     * charge them twice for one bundle.
     */
    @Test
    void whenTheAbandonedSessionWasActuallyPaid_thenTheRetryIsRefusedRatherThanChargingAgain() {
        givenValidPreconditions();
        givenQuote(List.of(givenMedia(UUID.randomUUID(), "4.00")), "4.00");
        givenAbandonedAttempt("cs_test_paid", "complete");

        Session previous = mock(Session.class);
        when(previous.getStatus()).thenReturn("complete");

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.retrieve("cs_test_paid")).thenReturn(previous);

            assertThrows(BundleSubscriptionAlreadyPaidException.class,
                    () -> service.createSubscriptionCheckout(jwt, BUNDLE_ID, CAMPAIGN_ID, BUSINESS_ID));

            sessions.verify(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)),
                    never());
        }
        verify(bundleSubscriptionRepository, never()).save(any());
    }

    // ---------- cancel: the one path that reaches Stripe (M5) ----------

    /**
     * Always {@code cancel_at_period_end}, never an immediate cancel — the advertiser
     * keeps the screens they paid for until the period ends.
     */
    @Test
    void whenCancelingAnActiveSubscription_thenStripeIsAskedToCancelAtPeriodEndOnly() throws StripeException {
        BundleSubscription active = givenLiveSubscription("sub_stripe_live");
        when(bundleSubscriptionRepository.findBySubscriptionId("sub-local-1"))
                .thenReturn(Optional.of(active));

        Subscription stripeSub = mock(Subscription.class);
        when(stripeSub.update(any(SubscriptionUpdateParams.class))).thenReturn(stripeSub);

        try (MockedStatic<Subscription> subscriptions = mockStatic(Subscription.class)) {
            subscriptions.when(() -> Subscription.retrieve("sub_stripe_live")).thenReturn(stripeSub);
            ArgumentCaptor<SubscriptionUpdateParams> params =
                    ArgumentCaptor.forClass(SubscriptionUpdateParams.class);

            assertDoesNotThrow(() -> service.cancelSubscription(jwt, "sub-local-1"));

            verify(stripeSub).update(params.capture());
            assertEquals(Boolean.TRUE, params.getValue().getCancelAtPeriodEnd());
        }

        // Mirrored locally so the advertiser sees it before the webhook lands.
        ArgumentCaptor<BundleSubscription> saved = ArgumentCaptor.forClass(BundleSubscription.class);
        verify(bundleSubscriptionRepository).save(saved.capture());
        assertTrue(saved.getValue().isCancelAtPeriodEnd());
        assertEquals(BundleSubscriptionStatus.ACTIVE, saved.getValue().getStatus(),
                "status only becomes CANCELED when Stripe reports the period ended");
    }

    @Test
    void whenCancelingAnUnknownSubscription_thenNotFound() {
        when(bundleSubscriptionRepository.findBySubscriptionId("nope")).thenReturn(Optional.empty());

        assertThrows(BundleSubscriptionNotFoundException.class,
                () -> service.cancelSubscription(jwt, "nope"));
    }

    private BundleSubscription givenLiveSubscription(String stripeSubscriptionId) {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId("sub-local-1");
        subscription.setBundleId(BUNDLE_ID);
        subscription.setAdvertiserBusinessId(BUSINESS_ID);
        subscription.setCampaignId(CAMPAIGN_ID);
        subscription.setStripeCheckoutSessionId(SESSION_ID);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        subscription.setMonthlyAmount(new BigDecimal("4.00"));
        subscription.setScreenCount(1);
        return subscription;
    }

    private void givenAbandonedAttempt(String previousSessionId, String previousStatus) {
        BundleSubscription incomplete = new BundleSubscription();
        incomplete.setSubscriptionId("sub-local-retry");
        incomplete.setBundleId(BUNDLE_ID);
        incomplete.setAdvertiserBusinessId(BUSINESS_ID);
        incomplete.setCampaignId(CAMPAIGN_ID);
        incomplete.setStripeCheckoutSessionId(previousSessionId);
        incomplete.setStatus(BundleSubscriptionStatus.INCOMPLETE);
        incomplete.setMonthlyAmount(new BigDecimal("4.00"));
        incomplete.setScreenCount(1);

        // Both lookups need stubbing: with one of them explicit, strict stubs treat the
        // other's arguments as an unexpected call rather than falling back to empty.
        when(bundleSubscriptionRepository.findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                BUNDLE_ID, BUSINESS_ID,
                Set.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE)))
                .thenReturn(Optional.empty());
        when(bundleSubscriptionRepository.findByBundleIdAndAdvertiserBusinessIdAndStatusIn(
                BUNDLE_ID, BUSINESS_ID, Set.of(BundleSubscriptionStatus.INCOMPLETE)))
                .thenReturn(Optional.of(incomplete));
    }

    // ---------- notifyMediaOwnersOfNewSubscription ----------
    // The email the legacy reservation flow used to send and P1 M6 silently dropped
    // when it deleted that flow wholesale.

    private static final String NOTIFY_SUB_ID = "sub-notify-1";

    @Test
    void notifyMediaOwners_emailsEachDistinctOwnerExactlyOnce() {
        givenNotifiableSubscription();
        Media ownerAScreen = givenMediaWithLocation(ownerA, "Downtown Billboard", "Complexe Desjardins", "Montreal");
        Media secondMontrealScreen =
                givenMediaWithLocation(ownerA, "Metro Panel", "Berri-UQAM", "Montreal");
        Media ownerBScreen = givenMediaWithLocation(ownerB, "Highway Sign", "Autoroute 15", "Laval");
        when(bundleSubscriptionItemRepository.findAllBySubscriptionId(NOTIFY_SUB_ID)).thenReturn(List.of(
                givenSubscriptionItem(ownerAScreen.getId(), ownerA),
                givenSubscriptionItem(secondMontrealScreen.getId(), ownerA), // same owner, must not double-email
                givenSubscriptionItem(ownerBScreen.getId(), ownerB)));
        // Map.groupingBy preserves each key's values in the source stream's order, so the
        // media-id list handed to each owner's lookup is deterministic here.
        when(mediaRepository.findAllByIdWithLocation(List.of(ownerAScreen.getId(), secondMontrealScreen.getId())))
                .thenReturn(List.of(ownerAScreen, secondMontrealScreen));
        when(mediaRepository.findAllByIdWithLocation(List.of(ownerBScreen.getId())))
                .thenReturn(List.of(ownerBScreen));
        givenResolvableOwnerEmail(ownerA.toString(), "auth0|ownerA", "ownerA@example.com");
        givenResolvableOwnerEmail(ownerB.toString(), "auth0|ownerB", "ownerB@example.com");

        service.notifyMediaOwnersOfNewSubscription(NOTIFY_SUB_ID);

        ArgumentCaptor<String> ownerABody = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ownerBBody = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSimpleEmail(eq("ownerA@example.com"), contains("Acme Co"), ownerABody.capture());
        verify(emailService).sendSimpleEmail(eq("ownerB@example.com"), contains("Acme Co"), ownerBBody.capture());
        verifyNoMoreInteractions(emailService);

        assertTrue(ownerABody.getValue().contains("Billboard Creative"));
        assertTrue(ownerABody.getValue().contains("https://cdn.example.com/ad.jpg"));
        assertTrue(ownerABody.getValue().contains("Full Network"), "must name the bundle the advertiser joined");
        assertTrue(ownerABody.getValue().contains("Downtown Billboard"), "owner A must see their own screen title");
        assertTrue(ownerABody.getValue().contains("Metro Panel"), "owner A must see their second screen too");
        assertTrue(ownerABody.getValue().contains("Complexe Desjardins"));
        assertTrue(ownerABody.getValue().contains("Montreal"));
        assertFalse(ownerABody.getValue().contains("Highway Sign"),
                "owner A must not see owner B's screen — each owner only sees their own");
        assertTrue(ownerABody.getValue().contains("You'll earn $8.00/month"),
                "owner A has two items at $4.00 each, so their total must be summed");

        assertTrue(ownerBBody.getValue().contains("Full Network"));
        assertTrue(ownerBBody.getValue().contains("Highway Sign"), "owner B must see their own screen title");
        assertTrue(ownerBBody.getValue().contains("Autoroute 15"));
        assertTrue(ownerBBody.getValue().contains("Laval"));
        assertFalse(ownerBBody.getValue().contains("Downtown Billboard"),
                "owner B must not see owner A's screens");
        assertTrue(ownerBBody.getValue().contains("You'll earn $4.00/month"));
    }

    @Test
    void notifyMediaOwners_unknownSubscription_isANoOp() {
        when(bundleSubscriptionRepository.findBySubscriptionId(NOTIFY_SUB_ID)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.notifyMediaOwnersOfNewSubscription(NOTIFY_SUB_ID));

        verifyNoInteractions(emailService, bundleSubscriptionItemRepository);
    }

    @Test
    void notifyMediaOwners_campaignNotFound_isANoOp() {
        BundleSubscription subscription = givenSubscriptionRow();
        when(bundleSubscriptionRepository.findBySubscriptionId(NOTIFY_SUB_ID))
                .thenReturn(Optional.of(subscription));
        when(adCampaignRepository.findByCampaignId_CampaignId(CAMPAIGN_ID)).thenReturn(null);

        assertDoesNotThrow(() -> service.notifyMediaOwnersOfNewSubscription(NOTIFY_SUB_ID));

        verifyNoInteractions(emailService);
    }

    @Test
    void notifyMediaOwners_campaignWithNoAds_isANoOp() {
        BundleSubscription subscription = givenSubscriptionRow();
        when(bundleSubscriptionRepository.findBySubscriptionId(NOTIFY_SUB_ID))
                .thenReturn(Optional.of(subscription));
        AdCampaign emptyCampaign = new AdCampaign();
        emptyCampaign.setCampaignId(new AdCampaignIdentifier(CAMPAIGN_ID));
        emptyCampaign.setAds(List.of());
        when(adCampaignRepository.findByCampaignId_CampaignId(CAMPAIGN_ID)).thenReturn(emptyCampaign);

        assertDoesNotThrow(() -> service.notifyMediaOwnersOfNewSubscription(NOTIFY_SUB_ID));

        verifyNoInteractions(emailService);
    }

    /** An owner whose email cannot be resolved must not block the other affected owners. */
    @Test
    void notifyMediaOwners_ownerWithNoResolvableEmail_isSkippedButOthersStillNotified() {
        givenNotifiableSubscription();
        when(bundleSubscriptionItemRepository.findAllBySubscriptionId(NOTIFY_SUB_ID)).thenReturn(List.of(
                givenSubscriptionItem(montrealScreen.getId(), ownerA),
                givenSubscriptionItem(lavalScreen.getId(), ownerB)));
        when(employeeRepository.findAllByBusinessId_BusinessId(ownerA.toString())).thenReturn(List.of());
        givenResolvableOwnerEmail(ownerB.toString(), "auth0|ownerB", "ownerB@example.com");

        service.notifyMediaOwnersOfNewSubscription(NOTIFY_SUB_ID);

        verify(emailService).sendSimpleEmail(eq("ownerB@example.com"), anyString(), anyString());
        verifyNoMoreInteractions(emailService);
    }

    /** A mail-server failure for one owner must not stop the send to the next owner. */
    @Test
    void notifyMediaOwners_oneOwnersSendFails_theOtherOwnerIsStillNotified() {
        givenNotifiableSubscription();
        when(bundleSubscriptionItemRepository.findAllBySubscriptionId(NOTIFY_SUB_ID)).thenReturn(List.of(
                givenSubscriptionItem(montrealScreen.getId(), ownerA),
                givenSubscriptionItem(lavalScreen.getId(), ownerB)));
        givenResolvableOwnerEmail(ownerA.toString(), "auth0|ownerA", "ownerA@example.com");
        givenResolvableOwnerEmail(ownerB.toString(), "auth0|ownerB", "ownerB@example.com");
        doThrow(new RuntimeException("mail server down"))
                .when(emailService).sendSimpleEmail(eq("ownerA@example.com"), anyString(), anyString());

        assertDoesNotThrow(() -> service.notifyMediaOwnersOfNewSubscription(NOTIFY_SUB_ID));

        verify(emailService).sendSimpleEmail(eq("ownerB@example.com"), anyString(), anyString());
    }

    private void givenNotifiableSubscription() {
        BundleSubscription subscription = givenSubscriptionRow();
        when(bundleSubscriptionRepository.findBySubscriptionId(NOTIFY_SUB_ID))
                .thenReturn(Optional.of(subscription));
        when(adCampaignRepository.findByCampaignId_CampaignId(CAMPAIGN_ID)).thenReturn(givenCampaignWithAds());
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(givenBusiness("Acme Co"));

        Bundle bundle = new Bundle();
        bundle.setBundleId(BUNDLE_ID);
        bundle.setNameEn("Full Network");
        bundle.setNameFr("Réseau complet");
        when(bundleRepository.findByBundleId(BUNDLE_ID)).thenReturn(Optional.of(bundle));
    }

    private BundleSubscription givenSubscriptionRow() {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId(NOTIFY_SUB_ID);
        subscription.setBundleId(BUNDLE_ID);
        subscription.setAdvertiserBusinessId(BUSINESS_ID);
        subscription.setCampaignId(CAMPAIGN_ID);
        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        subscription.setMonthlyAmount(new BigDecimal("4.00"));
        subscription.setScreenCount(1);
        return subscription;
    }

    private AdCampaign givenCampaignWithAds() {
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignId(new AdCampaignIdentifier(CAMPAIGN_ID));
        campaign.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        campaign.setName("Summer Promo");
        Ad ad = new Ad();
        ad.setAdIdentifier(new AdIdentifier());
        ad.setName("Billboard Creative");
        ad.setAdUrl("https://cdn.example.com/ad.jpg");
        ad.setAdType(AdType.IMAGE);
        campaign.setAds(List.of(ad));
        return campaign;
    }

    private BundleSubscriptionItem givenSubscriptionItem(UUID mediaId, UUID ownerBusinessId) {
        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(NOTIFY_SUB_ID);
        item.setMediaId(mediaId);
        item.setMediaOwnerBusinessId(ownerBusinessId.toString());
        item.setMonthlyAmount(new BigDecimal("4.00"));
        return item;
    }

    private void givenResolvableOwnerEmail(String ownerBusinessId, String userId, String email) {
        Employee employee = new Employee();
        employee.setUserId(userId);
        employee.setBusinessId(new BusinessIdentifier(ownerBusinessId));
        when(employeeRepository.findAllByBusinessId_BusinessId(ownerBusinessId)).thenReturn(List.of(employee));
        when(auth0Service.getUserEmailByUserId(userId)).thenReturn(email);
    }
}
