package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundlePayout;
import com.envisionad.webservice.payment.dataaccesslayer.BundlePayoutRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItem;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccountRepository;
import com.envisionad.webservice.utils.JwtUtils;
import com.envisionad.webservice.payment.exceptions.StripeAccountNotFoundException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.BalanceTransactionCollection;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.BalanceTransactionListParams;
import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Connect onboarding, account status, and the dashboard.
 * <p>
 * P1 M6 removed this class's checkout and payment-sync tests along with the methods they
 * covered. The dashboard tests below were rewritten rather than deleted: the metric definitions
 * are unchanged (brief req. 20), only their sources moved — advertiser figures now come from
 * {@code bundle_subscriptions} and media-owner earnings from the {@code bundle_payouts} ledger.
 */
@ExtendWith(MockitoExtension.class)
class StripeServiceUnitTest {

        private StripeServiceImpl stripeService;

        @Mock
        private StripeAccountRepository stripeAccountRepository;

        @Mock
        private MediaRepository mediaRepository;

        @Mock
        private BundleSubscriptionRepository bundleSubscriptionRepository;

        @Mock
        private BundleSubscriptionItemRepository bundleSubscriptionItemRepository;

        @Mock
        private BundlePayoutRepository bundlePayoutRepository;

        @Mock
        private JwtUtils jwtUtils;

        @BeforeEach
        void setUp() {
                stripeService = new StripeServiceImpl(stripeAccountRepository, mediaRepository,
                                bundleSubscriptionRepository, bundleSubscriptionItemRepository,
                                bundlePayoutRepository, jwtUtils);
        }

        // ========== Tests for createConnectedAccount ==========

        @Test
        void createConnectedAccount_shouldReturnExistingAccountId_whenAccountExists() {
                // Given
                String userId = "user-1";
                String businessId = "biz-1";
                String existingAccountId = "acct_existing123";

                org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                                .withTokenValue("token")
                                .header("alg", "none")
                                .claim("sub", userId)
                                .build();

                StripeAccount existingAccount = new StripeAccount();
                existingAccount.setBusinessId(businessId);
                existingAccount.setStripeAccountId(existingAccountId);
                existingAccount.setOnboardingComplete(true);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.of(existingAccount));

                // When
                String result = stripeService.createConnectedAccount(jwt, businessId);

                // Then
                assertEquals(existingAccountId, result);
                verify(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                verify(stripeAccountRepository).findByBusinessId(businessId);
                verify(stripeAccountRepository, never()).save(any(StripeAccount.class));
        }

        @Test
        void createConnectedAccount_shouldCreateNewAccount_whenAccountDoesNotExist() {
                // Given
                String userId = "user-1";
                String businessId = "biz-1";
                String newAccountId = "acct_new123";

                org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                                .withTokenValue("token")
                                .header("alg", "none")
                                .claim("sub", userId)
                                .build();

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.empty());

                // Mock Stripe Account creation
                Account mockAccount = mock(Account.class);
                when(mockAccount.getId()).thenReturn(newAccountId);

                try (MockedStatic<Account> accountMock = mockStatic(Account.class)) {
                        accountMock.when(() -> Account.create(any(AccountCreateParams.class))).thenReturn(mockAccount);

                        // When
                        String result = stripeService.createConnectedAccount(jwt, businessId);

                        // Then
                        assertEquals(newAccountId, result);
                        verify(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                        verify(stripeAccountRepository).findByBusinessId(businessId);
                        verify(stripeAccountRepository)
                                        .save(argThat(account -> account.getBusinessId().equals(businessId) &&
                                                        account.getStripeAccountId().equals(newAccountId) &&
                                                        !account.isOnboardingComplete()));
                }
        }

        // ========== Tests for createAccountLink ==========

        @Test
        void createAccountLink_shouldReturnOnboardingUrl() throws StripeException {
                // Given
                String stripeAccountId = "acct_123";
                String returnUrl = "https://example.com/return";
                String refreshUrl = "https://example.com/refresh";
                String expectedUrl = "https://connect.stripe.com/setup/test";

                AccountLink mockAccountLink = mock(AccountLink.class);
                when(mockAccountLink.getUrl()).thenReturn(expectedUrl);

                try (MockedStatic<AccountLink> accountLinkMock = mockStatic(AccountLink.class)) {
                        accountLinkMock.when(() -> AccountLink.create(any(AccountLinkCreateParams.class)))
                                        .thenReturn(mockAccountLink);

                        // When
                        String result = stripeService.createAccountLink(stripeAccountId, returnUrl, refreshUrl);

                        // Then
                        assertEquals(expectedUrl, result);
                }
        }

        // ========== Tests for createConnectedAccountAndLink ==========

        @Test
        void createConnectedAccountAndLink_shouldReturnAccountIdAndUrl() throws StripeException {
                // Given
                String userId = "user-1";
                String businessId = "biz-1";
                String accountId = "acct_123";
                String returnUrl = "https://example.com/return";
                String refreshUrl = "https://example.com/refresh";
                String onboardingUrl = "https://connect.stripe.com/setup/test";

                org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                                .withTokenValue("token")
                                .header("alg", "none")
                                .claim("sub", userId)
                                .build();

                StripeAccount existingAccount = new StripeAccount();
                existingAccount.setBusinessId(businessId);
                existingAccount.setStripeAccountId(accountId);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.of(existingAccount));

                AccountLink mockAccountLink = mock(AccountLink.class);
                when(mockAccountLink.getUrl()).thenReturn(onboardingUrl);

                try (MockedStatic<AccountLink> accountLinkMock = mockStatic(AccountLink.class)) {
                        accountLinkMock.when(() -> AccountLink.create(any(AccountLinkCreateParams.class)))
                                        .thenReturn(mockAccountLink);

                        // When
                        Map<String, String> result = stripeService.createConnectedAccountAndLink(jwt, businessId,
                                        returnUrl, refreshUrl);

                        // Then
                        assertEquals(accountId, result.get("accountId"));
                        assertEquals(onboardingUrl, result.get("onboardingUrl"));
                        assertEquals(2, result.size());
                }
        }

        // ========== Tests for getAccountStatus ==========

        @Test
        void getAccountStatus_shouldReturnNotConnected_whenAccountDoesNotExist() {
                // Given
                String userId = "user-1";
                String businessId = "biz-1";

                org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                                .withTokenValue("token")
                                .header("alg", "none")
                                .claim("sub", userId)
                                .build();

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.empty());

                // When
                Map<String, Object> status = stripeService.getAccountStatus(jwt, businessId);

                // Then
                assertEquals(false, status.get("connected"));
                assertEquals(false, status.get("onboardingComplete"));
                assertEquals(false, status.get("chargesEnabled"));
                assertEquals(false, status.get("payoutsEnabled"));
                assertNull(status.get("stripeAccountId"));
                verify(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
        }

        @Test
        void getAccountStatus_shouldReturnConnectedStatus_whenAccountExists() {
                // Given
                String userId = "user-1";
                String businessId = "biz-1";
                String stripeAccountId = "acct_123";

                org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                                .withTokenValue("token")
                                .header("alg", "none")
                                .claim("sub", userId)
                                .build();

                StripeAccount account = new StripeAccount();
                account.setBusinessId(businessId);
                account.setStripeAccountId(stripeAccountId);
                account.setOnboardingComplete(true);
                account.setChargesEnabled(true);
                account.setPayoutsEnabled(true);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.of(account));

                // When
                Map<String, Object> status = stripeService.getAccountStatus(jwt, businessId);

                // Then
                assertEquals(true, status.get("connected"));
                assertEquals(true, status.get("onboardingComplete"));
                assertEquals(true, status.get("chargesEnabled"));
                assertEquals(true, status.get("payoutsEnabled"));
                assertEquals(stripeAccountId, status.get("stripeAccountId"));
                verify(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
        }

        @Test
        void getAccountStatus_shouldReturnPartialStatus_whenOnboardingIncomplete() {
                // Given
                String userId = "user-1";
                String businessId = "biz-1";
                String stripeAccountId = "acct_123";

                org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                                .withTokenValue("token")
                                .header("alg", "none")
                                .claim("sub", userId)
                                .build();

                StripeAccount account = new StripeAccount();
                account.setBusinessId(businessId);
                account.setStripeAccountId(stripeAccountId);
                account.setOnboardingComplete(false);
                account.setChargesEnabled(false);
                account.setPayoutsEnabled(false);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.of(account));

                // When
                Map<String, Object> status = stripeService.getAccountStatus(jwt, businessId);

                // Then
                assertEquals(true, status.get("connected"));
                assertEquals(false, status.get("onboardingComplete"));
                assertEquals(false, status.get("chargesEnabled"));
                assertEquals(false, status.get("payoutsEnabled"));
                assertEquals(stripeAccountId, status.get("stripeAccountId"));
        }
        // ========== Tests for getDashboardData ==========

        private org.springframework.security.oauth2.jwt.Jwt jwtFor(String userId) {
                return org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
                                .header("alg", "none")
                                .claim("sub", userId)
                                .build();
        }

        private BundleSubscription subscription(String subscriptionId, String businessId, String amount) {
                BundleSubscription subscription = new BundleSubscription();
                subscription.setSubscriptionId(subscriptionId);
                subscription.setAdvertiserBusinessId(businessId);
                subscription.setMonthlyAmount(new BigDecimal(amount));
                subscription.setCreatedAt(LocalDateTime.now().minusDays(2));
                return subscription;
        }

        private BundlePayout payout(String businessId, String gross, String net) {
                BundlePayout bundlePayout = new BundlePayout();
                bundlePayout.setMediaOwnerBusinessId(businessId);
                bundlePayout.setGrossAmount(new BigDecimal(gross));
                bundlePayout.setAmount(new BigDecimal(net));
                bundlePayout.setCreatedAt(LocalDateTime.now().minusDays(1));
                return bundlePayout;
        }

        /**
         * Earnings are summed straight off the ledger. Note net is NOT recomputed as
         * {@code gross * (100 - feePercent)}: the ledger's {@code amount} is what was actually
         * transferred, so a later change to the platform fee cannot rewrite past earnings.
         */
        @Test
        void getDashboardData_shouldSumEarningsFromThePayoutLedger() {
                String userId = "user-1";
                String businessId = "biz-1";

                var jwt = jwtFor(userId);

                StripeAccount account = new StripeAccount();
                account.setBusinessId(businessId);
                account.setStripeAccountId("acct_123");
                account.setOnboardingComplete(true);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                doNothing().when(jwtUtils).validateUserIsEmployeeOfBusiness(userId, businessId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.of(account));
                when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(new ArrayList<>());
                when(bundlePayoutRepository.findAllByMediaOwnerBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(
                                                payout(businessId, "100.00", "70.00"),
                                                payout(businessId, "200.00", "140.00")));

                BalanceTransactionCollection mockPayouts = mock(BalanceTransactionCollection.class);
                when(mockPayouts.getData()).thenReturn(new ArrayList<>());

                try (MockedStatic<BalanceTransaction> balanceTransactionMock = mockStatic(BalanceTransaction.class)) {
                        balanceTransactionMock.when(() -> BalanceTransaction.list(
                                        any(BalanceTransactionListParams.class), any(RequestOptions.class)))
                                        .thenReturn(mockPayouts);

                        Map<String, Object> dashboard = stripeService.getDashboardData(jwt, businessId, "monthly");

                        assertNotNull(dashboard);
                        assertEquals(0, new BigDecimal("300.00")
                                        .compareTo((BigDecimal) dashboard.get("grossEarnings")));
                        assertEquals(0, new BigDecimal("210.00")
                                        .compareTo((BigDecimal) dashboard.get("netEarnings")));
                        assertEquals(0, new BigDecimal("90.00")
                                        .compareTo((BigDecimal) dashboard.get("platformFee")));
                        assertEquals(2, dashboard.get("paymentCount"));
                        assertEquals(true, dashboard.get("isMediaOwner"));
                        assertNotNull(dashboard.get("payouts"));
                }
        }

        /**
         * A SKIPPED or FAILED payout still represents money the owner earned, so it counts toward
         * gross while contributing nothing to net — which is precisely the discrepancy an owner
         * needs to be able to see.
         */
        @Test
        void getDashboardData_shouldCountUnpaidPayoutsInGrossButNotNet() {
                String userId = "user-1";
                String businessId = "biz-1";
                var jwt = jwtFor(userId);

                StripeAccount account = new StripeAccount();
                account.setBusinessId(businessId);
                account.setStripeAccountId("acct_123");

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.of(account));
                when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(new ArrayList<>());
                when(bundlePayoutRepository.findAllByMediaOwnerBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(payout(businessId, "42.00", "0.00")));

                BalanceTransactionCollection mockPayouts = mock(BalanceTransactionCollection.class);
                when(mockPayouts.getData()).thenReturn(new ArrayList<>());

                try (MockedStatic<BalanceTransaction> balanceTransactionMock = mockStatic(BalanceTransaction.class)) {
                        balanceTransactionMock.when(() -> BalanceTransaction.list(
                                        any(BalanceTransactionListParams.class), any(RequestOptions.class)))
                                        .thenReturn(mockPayouts);

                        Map<String, Object> dashboard = stripeService.getDashboardData(jwt, businessId, "monthly");

                        assertEquals(0, new BigDecimal("42.00")
                                        .compareTo((BigDecimal) dashboard.get("grossEarnings")));
                        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) dashboard.get("netEarnings")));
                }
        }

        /** Advertiser spend keeps the reservation era's booking basis: subscriptions created in the period. */
        @Test
        void getDashboardData_shouldSumAdvertiserSpendFromSubscriptionsCreatedInPeriod() {
                String userId = "user-1";
                String businessId = "biz-1";
                var jwt = jwtFor(userId);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.empty());
                when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(
                                                subscription("sub-1", businessId, "48.00"),
                                                subscription("sub-2", businessId, "3.20")));
                when(bundleSubscriptionItemRepository.findAllBySubscriptionId(anyString()))
                                .thenReturn(new ArrayList<>());
                when(mediaRepository.findAllById(any())).thenReturn(new ArrayList<>());

                Map<String, Object> dashboard = stripeService.getDashboardData(jwt, businessId, "monthly");

                assertEquals(0, new BigDecimal("51.20").compareTo((BigDecimal) dashboard.get("totalSpend")));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> payments = (List<Map<String, Object>>) dashboard.get("payments");
                assertEquals(2, payments.size());
                assertEquals(false, dashboard.get("isMediaOwner"));
        }

        @Test
        void getDashboardData_shouldReturnZeroes_whenBusinessHasNothing() {
                String userId = "user-1";
                String businessId = "biz-1";
                var jwt = jwtFor(userId);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.empty());
                when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(new ArrayList<>());

                Map<String, Object> dashboard = stripeService.getDashboardData(jwt, businessId, "monthly");

                assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) dashboard.get("totalSpend")));
                assertEquals(0L, dashboard.get("estimatedImpressions"));
                assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) dashboard.get("averageCPM")));
        }

        /**
         * The window is {@code max(createdAt, periodStart)} → {@code min(now, currentPeriodEnd)},
         * multiplied across every screen in the locked item set.
         */
        @Test
        void getDashboardData_shouldEstimateImpressionsAcrossTheLockedScreenSet() {
                String userId = "user-1";
                String businessId = "biz-1";
                var jwt = jwtFor(userId);

                BundleSubscription subscription = subscription("sub-1", businessId, "48.00");
                subscription.setCreatedAt(LocalDateTime.now().minusDays(10));
                subscription.setCurrentPeriodEnd(LocalDateTime.now().plusDays(20));

                UUID mediaA = UUID.randomUUID();
                UUID mediaB = UUID.randomUUID();

                BundleSubscriptionItem itemA = new BundleSubscriptionItem();
                itemA.setSubscriptionId("sub-1");
                itemA.setMediaId(mediaA);
                BundleSubscriptionItem itemB = new BundleSubscriptionItem();
                itemB.setSubscriptionId("sub-1");
                itemB.setMediaId(mediaB);

                Media screenA = new Media();
                screenA.setId(mediaA);
                screenA.setDailyImpressions(100);
                Media screenB = new Media();
                screenB.setId(mediaB);
                screenB.setDailyImpressions(50);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.empty());
                when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(subscription));
                when(bundleSubscriptionItemRepository.findAllBySubscriptionId("sub-1"))
                                .thenReturn(List.of(itemA, itemB));
                when(mediaRepository.findAllById(any())).thenReturn(List.of(screenA, screenB));

                Map<String, Object> dashboard = stripeService.getDashboardData(jwt, businessId, "monthly");

                // The monthly window opens 1 month ago; the subscription only became live 10 days
                // ago and is still running, so ~10 days × (100 + 50) impressions per day.
                long impressions = (long) dashboard.get("estimatedImpressions");
                assertEquals(10 * 150L, impressions);
                assertTrue(((BigDecimal) dashboard.get("averageCPM")).compareTo(BigDecimal.ZERO) > 0);
        }

        /**
         * A NULL {@code current_period_end} is real, reachable state on an ACTIVE row — only
         * {@code invoice.paid} sets that column, so a subscription activated by
         * {@code checkout.session.completed} has none. It must read as "still running", not as a
         * zero-length window that silently contributes no impressions.
         */
        @Test
        void getDashboardData_shouldTreatNullRenewalDateAsStillRunning() {
                String userId = "user-1";
                String businessId = "biz-1";
                var jwt = jwtFor(userId);

                BundleSubscription subscription = subscription("sub-1", businessId, "48.00");
                subscription.setCreatedAt(LocalDateTime.now().minusDays(5));
                subscription.setCurrentPeriodEnd(null);

                UUID mediaId = UUID.randomUUID();
                BundleSubscriptionItem item = new BundleSubscriptionItem();
                item.setSubscriptionId("sub-1");
                item.setMediaId(mediaId);

                Media screen = new Media();
                screen.setId(mediaId);
                screen.setDailyImpressions(200);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.empty());
                when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(subscription));
                when(bundleSubscriptionItemRepository.findAllBySubscriptionId("sub-1"))
                                .thenReturn(List.of(item));
                when(mediaRepository.findAllById(any())).thenReturn(List.of(screen));

                Map<String, Object> dashboard = stripeService.getDashboardData(jwt, businessId, "monthly");

                assertEquals(5 * 200L, (long) dashboard.get("estimatedImpressions"));
        }

        /** A screen with no dailyImpressions figure contributes nothing rather than throwing. */
        @Test
        void getDashboardData_shouldIgnoreScreensWithNoImpressionData() {
                String userId = "user-1";
                String businessId = "biz-1";
                var jwt = jwtFor(userId);

                BundleSubscription subscription = subscription("sub-1", businessId, "48.00");
                subscription.setCreatedAt(LocalDateTime.now().minusDays(5));

                UUID mediaId = UUID.randomUUID();
                BundleSubscriptionItem item = new BundleSubscriptionItem();
                item.setSubscriptionId("sub-1");
                item.setMediaId(mediaId);

                Media screen = new Media();
                screen.setId(mediaId);
                screen.setDailyImpressions(null);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.empty());
                when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(subscription));
                when(bundleSubscriptionItemRepository.findAllBySubscriptionId("sub-1"))
                                .thenReturn(List.of(item));
                when(mediaRepository.findAllById(any())).thenReturn(List.of(screen));

                Map<String, Object> dashboard = stripeService.getDashboardData(jwt, businessId, "monthly");

                assertEquals(0L, dashboard.get("estimatedImpressions"));
        }

        @Test
        void getDashboardData_shouldDefaultToMonthly_whenPeriodIsUnrecognised() {
                String userId = "user-1";
                String businessId = "biz-1";
                var jwt = jwtFor(userId);

                when(jwtUtils.extractUserId(jwt)).thenReturn(userId);
                when(stripeAccountRepository.findByBusinessId(businessId)).thenReturn(Optional.empty());
                when(bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                                eq(businessId), any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(new ArrayList<>());

                assertNotNull(stripeService.getDashboardData(jwt, businessId, "not-a-period"));
                assertNotNull(stripeService.getDashboardData(jwt, businessId, "weekly"));
                assertNotNull(stripeService.getDashboardData(jwt, businessId, "yearly"));
        }
}
