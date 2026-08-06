package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.payment.exceptions.StripeAccountNotFoundException;
import com.envisionad.webservice.payment.exceptions.StripeOnboardingIncompleteException;
import com.envisionad.webservice.utils.JwtUtils;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.net.RequestOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.*;

@Slf4j
@Service
public class StripeServiceImpl implements StripeService {
    private final StripeAccountRepository stripeAccountRepository;
    private final MediaRepository mediaRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final BundleSubscriptionItemRepository bundleSubscriptionItemRepository;
    private final BundlePayoutRepository bundlePayoutRepository;
    private final JwtUtils jwtUtils;

    public StripeServiceImpl(StripeAccountRepository stripeAccountRepository,
            MediaRepository mediaRepository,
            BundleSubscriptionRepository bundleSubscriptionRepository,
            BundleSubscriptionItemRepository bundleSubscriptionItemRepository,
            BundlePayoutRepository bundlePayoutRepository,
            JwtUtils jwtUtils) {
        this.stripeAccountRepository = stripeAccountRepository;
        this.mediaRepository = mediaRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.bundleSubscriptionItemRepository = bundleSubscriptionItemRepository;
        this.bundlePayoutRepository = bundlePayoutRepository;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public String createConnectedAccount(Jwt jwt, String businessId) {
        // Ensure caller is an employee of the business before creating a connected account
        String userId = jwtUtils.extractUserId(jwt);
        jwtUtils.validateUserIsEmployeeOfBusiness(userId, businessId);

        // Check if account already exists
        return stripeAccountRepository.findByBusinessId(businessId)
                .map(StripeAccount::getStripeAccountId)
                .orElseGet(() -> {
                    try {
                        AccountCreateParams params = AccountCreateParams.builder()
                                .setType(AccountCreateParams.Type.EXPRESS)
                                .setCapabilities(
                                        AccountCreateParams.Capabilities.builder()
                                                .setCardPayments(
                                                        AccountCreateParams.Capabilities.CardPayments.builder()
                                                                .setRequested(true)
                                                                .build())
                                                .setTransfers(
                                                        AccountCreateParams.Capabilities.Transfers.builder()
                                                                .setRequested(true)
                                                                .build())
                                                .build())
                                .build();

                        Account account = Account.create(params);

                        StripeAccount stripeAccount = new StripeAccount();
                        stripeAccount.setBusinessId(businessId);
                        stripeAccount.setStripeAccountId(account.getId());
                        stripeAccount.setOnboardingComplete(false);
                        stripeAccountRepository.save(stripeAccount);

                        return account.getId();
                    } catch (StripeException e) {
                        throw new RuntimeException("Failed to create Stripe account", e);
                    }
                });
    }

    @Override
    public String createAccountLink(String stripeAccountId, String returnUrl, String refreshUrl)
            throws StripeException {
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(stripeAccountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        AccountLink accountLink = AccountLink.create(params);
        return accountLink.getUrl();
    }

    @Override
    public Map<String, String> createConnectedAccountAndLink(Jwt jwt, String businessId, String returnUrl,
            String refreshUrl) throws StripeException {
        String accountId = createConnectedAccount(jwt, businessId);
        String link = createAccountLink(accountId, returnUrl, refreshUrl);
        Map<String, String> resp = new HashMap<>();
        resp.put("accountId", accountId);
        resp.put("onboardingUrl", link);
        return resp;
    }

    /**
     * Get Stripe account status for a business
     */
    @Override
    public Map<String, Object> getAccountStatus(Jwt jwt, String businessId) {
        // Validate the user is an employee of the business
        String userId = jwtUtils.extractUserId(jwt);
        jwtUtils.validateUserIsEmployeeOfBusiness(userId, businessId);

        Map<String, Object> status = new HashMap<>();

        Optional<StripeAccount> accountOpt = stripeAccountRepository.findByBusinessId(businessId);

        if (accountOpt.isEmpty()) {
            status.put("connected", false);
            status.put("onboardingComplete", false);
            status.put("chargesEnabled", false);
            status.put("payoutsEnabled", false);
        } else {
            StripeAccount account = accountOpt.get();
            status.put("connected", true);
            status.put("onboardingComplete", account.isOnboardingComplete());
            status.put("chargesEnabled", account.isChargesEnabled());
            status.put("payoutsEnabled", account.isPayoutsEnabled());
            status.put("stripeAccountId", account.getStripeAccountId());
        }

        return status;
    }


    /**
     * Advertiser spend / media-owner earnings dashboard.
     * <p>
     * Re-sourced in P1 M6 (brief req. 20 — "keep the metric definitions; swap the source"). Both
     * of the old sources are gone: advertiser figures came from {@code reservations} and
     * media-owner figures from {@code payment_intents}, and both tables were dropped with the
     * weekly-reservation system. The replacements are {@code bundle_subscriptions} for what the
     * advertiser bought and the {@code bundle_payouts} ledger for what an owner was actually paid.
     */
    @Override
    public Map<String, Object> getDashboardData(Jwt jwt, String businessId, String period) {
        String userId = jwtUtils.extractUserId(jwt);
        jwtUtils.validateUserIsEmployeeOfBusiness(userId, businessId);

        // Check if the business has a connected Stripe account (likely a Media Owner)
        Optional<StripeAccount> accountOpt = stripeAccountRepository.findByBusinessId(businessId);
        Map<String, Object> dashboard = new HashMap<>();

        // The syncPendingPayments() reconciliation sweep that used to run here died with
        // payment_intents. Its bundle-era equivalent — detecting an ACTIVE subscription with no
        // payout row for the current period — does not exist yet and is tracked for M7.

        // 1. Determine Date Range
        LocalDateTime startDate = calculateStartDate(period);
        LocalDateTime endDate = LocalDateTime.now();

        // 2. Advertiser spend, on the same "booking basis" the reservation version used: a
        // subscription counts toward the period it was CREATED in, not every period it renews in.
        List<BundleSubscription> subscriptions =
                bundleSubscriptionRepository.findAllByAdvertiserBusinessIdAndCreatedAtBetween(
                        businessId, startDate, endDate);

        BigDecimal totalSpend = subscriptions.stream()
                .map(BundleSubscription::getMonthlyAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> advertiserPaymentList = subscriptions.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("amount", s.getMonthlyAmount());
            map.put("created", s.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
            map.put("currency", "CAD");
            return map;
        }).toList();

        dashboard.put("totalSpend", totalSpend);
        dashboard.put("payments", advertiserPaymentList);

        // 3. Estimated impressions: each subscription's locked screen set × each screen's daily
        // impressions × the days that subscription was live inside the window.
        long totalImpressions = estimateImpressions(subscriptions, startDate, endDate);
        dashboard.put("estimatedImpressions", totalImpressions);

        log.info("Dashboard data for {}: totalSpend={}, subscriptionCount={}", businessId, totalSpend,
                advertiserPaymentList.size());

        // Default to not media owner unless found below
        dashboard.put("isMediaOwner", false);

        if (accountOpt.isPresent()) {
            // SCENARIO: Media Owner - Show Earnings and Payouts
            StripeAccount stripeAccount = accountOpt.get();

            // Earnings come from the payout ledger rather than being recomputed. bundle_payouts
            // records what was actually transferred, so a later change to
            // stripe.platform-fee-percent cannot retroactively rewrite past earnings — which is
            // exactly what recomputing `gross * (100 - fee)` on every read used to do.
            List<BundlePayout> payoutRecords =
                    bundlePayoutRepository.findAllByMediaOwnerBusinessIdAndCreatedAtBetween(
                            businessId, startDate, endDate);

            BigDecimal grossEarnings = payoutRecords.stream()
                    .map(BundlePayout::getGrossAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netEarnings = payoutRecords.stream()
                    .map(BundlePayout::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<Map<String, Object>> revenuePaymentList = payoutRecords.stream().map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("amount", p.getGrossAmount());
                map.put("created", p.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
                map.put("currency", "CAD");
                return map;
            }).toList();
            dashboard.put("revenuePayments", revenuePaymentList);

            // Fetch payout history from Stripe
            try {
                BalanceTransactionCollection payouts = BalanceTransaction.list(
                        BalanceTransactionListParams.builder()
                                .setLimit(100L)
                                .build(),
                        RequestOptions.builder()
                                .setStripeAccount(stripeAccount.getStripeAccountId())
                                .build());
                dashboard.put("payouts", payouts.getData());
            } catch (StripeException e) {
                log.warn("Failed to fetch payouts for business {}: {}", businessId, e.getMessage());
                dashboard.put("payouts", Collections.emptyList());
            }

            dashboard.put("grossEarnings", grossEarnings);
            dashboard.put("netEarnings", netEarnings);
            dashboard.put("platformFee", grossEarnings.subtract(netEarnings));
            dashboard.put("paymentCount", payoutRecords.size());
            dashboard.put("advertiserPaymentCount", subscriptions.size());
            dashboard.put("isMediaOwner", true);

        }

        // Finalize CPM
        BigDecimal totalSpendVal = (BigDecimal) dashboard.getOrDefault("totalSpend", BigDecimal.ZERO);
        BigDecimal cpm = BigDecimal.ZERO;
        if (totalImpressions > 0 && totalSpendVal.compareTo(BigDecimal.ZERO) > 0) {
            // CPM (cost per 1000 impressions) with spend in dollars:
            // CPM = (spendInDollars / impressions) * 1000
            try {
                cpm = totalSpendVal.multiply(BigDecimal.valueOf(1000))
                        .divide(BigDecimal.valueOf(totalImpressions), 2, java.math.RoundingMode.HALF_UP);
            } catch (Exception e) {
                log.error("Error calculating CPM", e);
            }
        }
        dashboard.put("averageCPM", cpm);

        return dashboard;
    }

    /**
     * Impressions delivered by the advertiser's subscriptions inside the reporting window.
     * <p>
     * A subscription has no start/end pair the way a reservation did, so the live window is
     * derived: it opens at {@code created_at} and closes at {@code current_period_end}. That
     * column is legitimately NULL on an ACTIVE row — {@code checkout.session.completed} activates
     * a subscription without setting a renewal date, and only {@code invoice.paid} populates it —
     * so a NULL is read as "still running", not as a zero-length window.
     */
    private long estimateImpressions(List<BundleSubscription> subscriptions,
                                     LocalDateTime windowStart,
                                     LocalDateTime windowEnd) {
        if (subscriptions.isEmpty()) {
            return 0L;
        }

        // Batch-fetch every screen across every subscription's locked item set, to avoid an N+1.
        Map<String, List<BundleSubscriptionItem>> itemsBySubscription = new HashMap<>();
        Set<UUID> mediaIds = new HashSet<>();
        for (BundleSubscription s : subscriptions) {
            List<BundleSubscriptionItem> items =
                    bundleSubscriptionItemRepository.findAllBySubscriptionId(s.getSubscriptionId());
            itemsBySubscription.put(s.getSubscriptionId(), items);
            items.forEach(i -> mediaIds.add(i.getMediaId()));
        }

        Map<UUID, Media> mediaMap = new HashMap<>();
        for (Media m : mediaRepository.findAllById(mediaIds)) {
            mediaMap.put(m.getId(), m);
        }

        long totalImpressions = 0L;
        for (BundleSubscription s : subscriptions) {
            LocalDateTime liveFrom = s.getCreatedAt();
            LocalDateTime liveUntil = s.getCurrentPeriodEnd() != null ? s.getCurrentPeriodEnd() : windowEnd;

            LocalDateTime effectiveStart = liveFrom.isAfter(windowStart) ? liveFrom : windowStart;
            LocalDateTime effectiveEnd = liveUntil.isBefore(windowEnd) ? liveUntil : windowEnd;

            if (!effectiveEnd.isAfter(effectiveStart)) {
                continue;
            }

            java.time.Duration live = java.time.Duration.between(effectiveStart, effectiveEnd);
            long days = live.toDays();
            if (days == 0 && live.toHours() > 0) {
                days = 1;
            }

            for (BundleSubscriptionItem item : itemsBySubscription.getOrDefault(s.getSubscriptionId(), List.of())) {
                Media media = mediaMap.get(item.getMediaId());
                if (media != null && media.getDailyImpressions() != null) {
                    totalImpressions += days * media.getDailyImpressions();
                }
            }
        }
        return totalImpressions;
    }

    private LocalDateTime calculateStartDate(String period) {
        return switch (period.toLowerCase()) {
            case "weekly" -> LocalDateTime.now().minusWeeks(1);
            case "monthly" -> LocalDateTime.now().minusMonths(1);
            case "yearly" -> LocalDateTime.now().minusYears(1);
            default -> LocalDateTime.now().minusMonths(1);
        };
    }

}
