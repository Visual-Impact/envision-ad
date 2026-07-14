package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.advertisement.exceptions.AdCampaignNotFoundException;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.exceptions.MediaNotFoundException;
import com.envisionad.webservice.payment.dataaccesslayer.*;
import com.envisionad.webservice.payment.dataaccesslayer.Currency;
import com.envisionad.webservice.payment.dataaccesslayer.PaymentIntent;
import com.envisionad.webservice.payment.exceptions.DuplicatePaymentException;
import com.envisionad.webservice.payment.exceptions.InvalidPricingException;
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
import com.envisionad.webservice.reservation.dataaccesslayer.Reservation;
import com.envisionad.webservice.reservation.dataaccesslayer.ReservationRepository;
import com.envisionad.webservice.reservation.dataaccesslayer.ReservationStatus;

@Slf4j
@Service
public class StripeServiceImpl implements StripeService {
    private final StripeAccountRepository stripeAccountRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final AdCampaignRepository adCampaignRepository;
    private final MediaRepository mediaRepository;
    private final ReservationRepository reservationRepository;
    private final JwtUtils jwtUtils;
    private final StripeWebhookService stripeWebhookService;

    @Value("${stripe.platform-fee-percent}")
    private int platformFeePercent;

    public StripeServiceImpl(StripeAccountRepository stripeAccountRepository,
            PaymentIntentRepository paymentIntentRepository,
            AdCampaignRepository adCampaignRepository,
            MediaRepository mediaRepository,
            ReservationRepository reservationRepository,
            JwtUtils jwtUtils,
            StripeWebhookService stripeWebhookService) {
        this.stripeAccountRepository = stripeAccountRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.adCampaignRepository = adCampaignRepository;
        this.mediaRepository = mediaRepository;
        this.reservationRepository = reservationRepository;
        this.jwtUtils = jwtUtils;
        this.stripeWebhookService = stripeWebhookService;
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

    @Transactional
    @Override
    public Map<String, String> createCheckoutSession(String reservationId, BigDecimal amount, String businessId)
            throws StripeException {
        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPricingException(amount);
        }

        // Check for duplicate payment - only prevent if payment already SUCCEEDED
        // Allow retries for pending payments (incomplete Stripe sessions)
        Optional<PaymentIntent> existingPayment = paymentIntentRepository.findByReservationId(reservationId);
        if (existingPayment.isPresent()) {
            PaymentIntent existing = existingPayment.get();
            PaymentStatus status = existing.getStatus();

            // Only block if payment already succeeded
            if (status == PaymentStatus.SUCCEEDED) {
                log.warn("Payment already completed for reservation: {}", reservationId);
                throw new DuplicatePaymentException(reservationId);
            }

            // Allow retry for PENDING or FAILED payments
            log.info("Retrying payment for reservation: {} (previous status: {})",
                    reservationId, status);
        }

        // Fetch Stripe account for the business
        StripeAccount stripeAccount = stripeAccountRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new StripeAccountNotFoundException(businessId));

        if (!stripeAccount.isOnboardingComplete()) {
            throw new StripeOnboardingIncompleteException(businessId);
        }

        // Round to 2 decimals (HALF_UP) and convert to cents
        BigDecimal scaledAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        long amountInCents = scaledAmount.multiply(BigDecimal.valueOf(100)).longValueExact();

        long platformFee = BigDecimal.valueOf(amountInCents)
                .multiply(BigDecimal.valueOf(platformFeePercent))
                .divide(BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP)
                .longValueExact();

        // Use timestamp in idempotency key to allow fresh sessions for retries
        // This ensures each payment attempt gets a new Stripe session
        String idempotencyKey = reservationId + "-checkout-" + System.currentTimeMillis();
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        log.info("Creating checkout session for reservation: {}, amount: ${}, business: {}",
                reservationId, amount, businessId);

        // Create Checkout Session in Embedded Mode
        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setUiMode(SessionCreateParams.UiMode.EMBEDDED)
                        .setRedirectOnCompletion(SessionCreateParams.RedirectOnCompletion.NEVER)
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setPriceData(
                                                SessionCreateParams.LineItem.PriceData.builder()
                                                        .setCurrency(Currency.CAD.toString().toLowerCase())
                                                        .setUnitAmount(amountInCents)
                                                        .setProductData(
                                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                        .setName("Media Reservation")
                                                                        .setDescription("Reservation ID: " + reservationId)
                                                                        .build()
                                                        )
                                                        .build()
                                        )
                                        .setQuantity(1L)
                                        .build()
                        )
                        .setPaymentIntentData(
                                SessionCreateParams.PaymentIntentData.builder()
                                        .setApplicationFeeAmount(platformFee)
                                        .setTransferData(
                                                SessionCreateParams.PaymentIntentData.TransferData.builder()
                                                        .setDestination(stripeAccount.getStripeAccountId())
                                                        .build()
                                        )
                                        .putMetadata("reservationId", reservationId)
                                        .putMetadata("businessId", businessId)
                                        .build()
                        )
                        .build();

        Session session = Session.create(params, requestOptions);

        // In embedded mode, payment_intent is not immediately available
        // We'll track it via session ID and update via webhook
        log.info("Checkout session created: {}", session.getId());

        // Reuse existing PaymentIntent record or create new one
        PaymentIntent paymentIntent = existingPayment.orElse(new PaymentIntent());

        // Update/set fields for this payment attempt
        paymentIntent.setStripeSessionId(session.getId());
        paymentIntent.setReservationId(reservationId);
        paymentIntent.setBusinessId(businessId);
        paymentIntent.setAmount(amount);
        paymentIntent.setStatus(PaymentStatus.PENDING);
        paymentIntent.setUpdatedAt(LocalDateTime.now());

        // Clear old Stripe payment intent ID if retrying (will be set by webhook)
        if (paymentIntent.getId() != null) {
            log.info("Reusing existing PaymentIntent record for retry: reservationId={}", reservationId);
            paymentIntent.setStripePaymentIntentId(null);
        }

        paymentIntentRepository.save(paymentIntent);

        log.info("Checkout session created successfully: {} for reservation: {}", session.getId(), reservationId);

        // Return client_secret for embedded checkout
        Map<String, String> response = new HashMap<>();
        response.put("clientSecret", session.getClientSecret());
        response.put("sessionId", session.getId());
        return response;
    }

    @Transactional
    @Override
    public Map<String, String> createAuthorizedCheckoutSession(Jwt jwt, String campaignId, String mediaId,
            String reservationId,
            LocalDateTime startDate, LocalDateTime endDate) throws StripeException {
        // 1. Validate that the campaign exists
        AdCampaign campaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
        if (campaign == null) {
            log.warn("Payment attempt for non-existent campaign: {} by user: {}", campaignId, jwt != null ? jwt.getSubject() : null);
            throw new AdCampaignNotFoundException(campaignId);
        }

        // 2. SECURITY: Validate that the user owns this campaign
        String userId = jwtUtils.extractUserId(jwt);
        String advertiserBusinessId = campaign.getBusinessId().getBusinessId();
        jwtUtils.validateUserIsEmployeeOfBusiness(userId, advertiserBusinessId);
        log.info("User {} authorized to create payment for campaign {} (business: {})",
                 userId, campaignId, advertiserBusinessId);

        // 3. Validate that the media exists
        UUID mediaUuid = UUID.fromString(mediaId);
        Media media = mediaRepository.findById(mediaUuid)
                .orElseThrow(() -> {
                    log.warn("Payment attempt for non-existent media: {} by user: {}", mediaId, jwt.getSubject());
                    return new MediaNotFoundException("Media not found: " + mediaId);
                });

        // 4. Get the media owner's business ID
        String mediaOwnerBusinessId = media.getBusinessId().toString();
        log.info("Creating payment for media {} (owner: {}) from advertiser business: {}",
                 mediaId, mediaOwnerBusinessId, advertiserBusinessId);

        // 5. Calculate price on the backend
        BigDecimal calculatedAmount = calculatePriceFromDates(media.getPrice(), startDate, endDate);
        log.info("Calculated payment amount: ${} for media: {} (duration: {} to {})",
                 calculatedAmount, mediaId, startDate, endDate);

        // 6. Create the checkout session
        return createCheckoutSession(reservationId, calculatedAmount, mediaOwnerBusinessId);
    }

    /**
     * Calculate price based on media price and reservation date range.
     * Uses the same logic as frontend for consistency but executed server-side for
     * security.
     *
     * @param mediaPrice The base price of the media
     * @param startDate  The reservation start date
     * @param endDate    The reservation end date
     * @return Calculated total price
     */
    private BigDecimal calculatePriceFromDates(BigDecimal mediaPrice, LocalDateTime startDate, LocalDateTime endDate) {
        // Validate inputs
        if (mediaPrice == null || mediaPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Invalid media price: {}", mediaPrice);
            throw new InvalidPricingException(mediaPrice);
        }

        if (startDate == null || endDate == null) {
            log.error("Invalid date range: start={}, end={}", startDate, endDate);
            throw new InvalidPricingException(BigDecimal.ZERO);
        }

        // Calculate duration in days using Java time API
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(
                startDate.toLocalDate(),
                endDate.toLocalDate());

        if (totalDays < 0) {
            log.error("Invalid date range: end date is before start date");
            throw new InvalidPricingException(BigDecimal.ZERO);
        }

        long weeks = Math.max(1, (long) Math.ceil(totalDays / 7.0));
        BigDecimal totalPrice = mediaPrice.multiply(BigDecimal.valueOf(weeks));

        log.info("Price calculation: mediaPrice={}, days={}, weeks={}, total={}",
                mediaPrice, totalDays, weeks, totalPrice);

        return totalPrice;
    }

    @Override
    public Map<String, Object> getDashboardData(Jwt jwt, String businessId, String period) {
        String userId = jwtUtils.extractUserId(jwt);
        jwtUtils.validateUserIsEmployeeOfBusiness(userId, businessId);

        // Check if the business has a connected Stripe account (likely a Media Owner)
        Optional<StripeAccount> accountOpt = stripeAccountRepository.findByBusinessId(businessId);
        Map<String, Object> dashboard = new HashMap<>();

        // SYNC: Check for pending payments
        log.info("Checking for pending payments for advertiser: {}", businessId);
        syncPendingPayments(businessId);

        // 1. Determine Date Range
        LocalDateTime startDate = calculateStartDate(period);
        LocalDateTime endDate = LocalDateTime.now();

        // 2. Calculate Estimated Impressions and CPM
        List<Reservation> reservations = reservationRepository.findConfirmedReservationsByAdvertiserIdAndDateRange(
                businessId, startDate, endDate);

        // Batch fetch Media to avoid N+1 query
        Set<UUID> mediaIds = new HashSet<>();
        for (Reservation r : reservations) {
            mediaIds.add(r.getMediaId());
        }
        List<Media> medias = mediaRepository.findAllById(mediaIds);
        Map<UUID, Media> mediaMap = new HashMap<>();
        for (Media m : medias) {
            mediaMap.put(m.getId(), m);
        }

        long totalImpressions = 0;
        for (Reservation reservation : reservations) {
            // Calculate intersection of reservation duration and selected period
            LocalDateTime effectiveStart = reservation.getStartDate().isAfter(startDate) ? reservation.getStartDate()
                    : startDate;
            LocalDateTime effectiveEnd = reservation.getEndDate().isBefore(endDate) ? reservation.getEndDate()
                    : endDate;

            if (effectiveEnd.isAfter(effectiveStart)) {
                long days = java.time.Duration.between(effectiveStart, effectiveEnd).toDays();
                if (days == 0 && java.time.Duration.between(effectiveStart, effectiveEnd).toHours() > 0) {
                    days = 1;
                }

                // Use batched Media map
                Media media = mediaMap.get(reservation.getMediaId());
                if (media != null) {
                    Integer dailyImpressions = media.getDailyImpressions();
                    if (dailyImpressions != null) {
                        totalImpressions += (days * dailyImpressions);
                    }
                }
            }
        }

        dashboard.put("estimatedImpressions", totalImpressions);

        // 3. Always calculate Advertiser Spend (Outgoing Payments)
        // REPLACEMENT: Use Reservations instead of PaymentIntents per user request

        // Filter reservations that "started" in this period (Booking Basis)
        List<Reservation> newReservations = reservations.stream()
                // Inclusive check: startDate <= r.startDate <= endDate
                .filter(r -> !r.getStartDate().isBefore(startDate) && !r.getStartDate().isAfter(endDate))
                .toList();

        BigDecimal totalSpend = newReservations.stream()
                .map(Reservation::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Map to "payments" structure for frontend graph
        List<Map<String, Object>> advertiserPaymentList = newReservations.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("amount", r.getTotalPrice());
            map.put("created", r.getStartDate().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
            map.put("currency", "CAD"); // Default currency
            return map;
        }).toList();

        dashboard.put("totalSpend", totalSpend);
        dashboard.put("payments", advertiserPaymentList);

        log.info("Dashboard data for {}: totalSpend={}, paymentCount={}", businessId, totalSpend,
                advertiserPaymentList.size());

        // Default to not media owner unless found below
        dashboard.put("isMediaOwner", false);

        if (accountOpt.isPresent()) {
            // SCENARIO: Media Owner - Show Earnings and Payouts
            StripeAccount stripeAccount = accountOpt.get();

            // Get all successful payments REVENUE for this media owner
            List<PaymentIntent> revenuePayments = paymentIntentRepository
                    .findSuccessfulPaymentsByBusinessIdAndDateRange(
                            businessId,
                            startDate,
                            LocalDateTime.now());

            // Calculate gross earnings (total before platform fee)
            BigDecimal grossEarnings = revenuePayments.stream()
                    .map(PaymentIntent::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calculate net earnings (after platform fee)
            BigDecimal netEarnings = grossEarnings
                    .multiply(BigDecimal.valueOf(100 - platformFeePercent))
                    .divide(BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);

            List<Map<String, Object>> revenuePaymentList = revenuePayments.stream().map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("amount", p.getAmount());
                map.put("created", p.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
                map.put("currency", p.getCurrency());
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
            dashboard.put("paymentCount", revenuePayments.size());
            dashboard.put("advertiserPaymentCount", newReservations.size());
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

    private LocalDateTime calculateStartDate(String period) {
        return switch (period.toLowerCase()) {
            case "weekly" -> LocalDateTime.now().minusWeeks(1);
            case "monthly" -> LocalDateTime.now().minusMonths(1);
            case "yearly" -> LocalDateTime.now().minusYears(1);
            default -> LocalDateTime.now().minusMonths(1);
        };
    }

    private void syncPendingPayments(String businessId) {
        log.info("Starting syncPendingPayments for businessId: {}", businessId);
        try {
            List<PaymentIntent> pendingPayments = paymentIntentRepository.findPendingPaymentsByAdvertiserId(businessId);
            log.info("Found {} pending payments for businessId: {}", pendingPayments.size(), businessId);

            for (PaymentIntent p : pendingPayments) {
                log.info("Checking Stripe session for payment: {}", p.getId());
                if (p.getStripeSessionId() != null) {
                    try {
                        Session session = Session.retrieve(p.getStripeSessionId());
                        log.info("Stripe session status for {}: {}", p.getStripeSessionId(), session.getStatus());

                        if ("complete".equals(session.getStatus())) {
                            p.setStatus(PaymentStatus.SUCCEEDED);
                            // If we have the PI ID now, save it
                            if (session.getPaymentIntent() != null) {
                                p.setStripePaymentIntentId(session.getPaymentIntent());
                            }
                            paymentIntentRepository.save(p);
                            log.info("Synced pending payment {} to SUCCEEDED", p.getId());

                            // The webhook that normally confirms the reservation may not have
                            // arrived yet (or at all, e.g. local dev without webhook forwarding).
                            // Reconcile the reservation here too so it doesn't stay stuck as PENDING.
                            stripeWebhookService.updateReservationStatus(p.getReservationId(), ReservationStatus.CONFIRMED);
                        } else if ("expired".equals(session.getStatus())) {
                            p.setStatus(PaymentStatus.FAILED);
                            paymentIntentRepository.save(p);
                            log.info("Synced pending payment {} to FAILED", p.getId());
                        }
                    } catch (StripeException e) {
                        log.warn("Failed to sync Stripe session {}: {}", p.getStripeSessionId(), e.getMessage());
                    }
                } else {
                    log.warn("PaymentIntent {} has no Stripe Session ID", p.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error verifying pending payments for business {}:", businessId, e);
        }
    }
}
