package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.*;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePriceQuote;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePricingService;
import com.envisionad.webservice.bundle.businesslogiclayer.BundleService;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import com.envisionad.webservice.bundle.exceptions.BundleNoEligibleMediaException;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.utils.JwtUtils;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
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
    @Mock private JwtUtils jwtUtils;

    private Jwt jwt;
    private Media montrealScreen;
    private Media lavalScreen;
    private UUID ownerA;
    private UUID ownerB;

    @BeforeEach
    void setUp() {
        service = new BundleSubscriptionServiceImpl(bundleService, pricingService, adCampaignRepository,
                businessRepository, stripeCustomerRepository, bundleSubscriptionRepository,
                bundleSubscriptionItemRepository, jwtUtils);

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
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(givenBusiness("Acme Coffee"));

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
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(givenBusiness("Acme Coffee"));

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

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
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

    // ---------- fixtures ----------

    private void givenValidPreconditions() {
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
        when(pricingService.quote(BUNDLE_ID, BUSINESS_ID))
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
        return business;
    }

    private Customer givenStripeCustomer() {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        return customer;
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
}
