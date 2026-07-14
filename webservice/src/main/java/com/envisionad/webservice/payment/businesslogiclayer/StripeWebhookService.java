package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.advertisement.businesslogiclayer.AdCampaignService;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.advertisement.exceptions.AdCampaignNotFoundException;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.exceptions.MediaNotFoundException;
import com.envisionad.webservice.payment.dataaccesslayer.PaymentIntent;
import com.envisionad.webservice.payment.dataaccesslayer.PaymentIntentRepository;
import com.envisionad.webservice.payment.dataaccesslayer.PaymentStatus;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccountRepository;
import com.envisionad.webservice.reservation.dataaccesslayer.Reservation;
import com.envisionad.webservice.reservation.dataaccesslayer.ReservationRepository;
import com.envisionad.webservice.reservation.dataaccesslayer.ReservationStatus;
import com.envisionad.webservice.utils.EmailService;
import com.stripe.model.Account;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class StripeWebhookService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final ReservationRepository reservationRepository;
    private final EmailService emailService;
    private final EmployeeRepository employeeRepository;
    private final MediaRepository mediaRepository;
    private final AdCampaignRepository adCampaignRepository;
    private final AdCampaignService adCampaignService;
    private final StripeAccountRepository stripeAccountRepository;
    private final Auth0Service auth0Service;


    public StripeWebhookService(PaymentIntentRepository paymentIntentRepository,
                                ReservationRepository reservationRepository, EmailService emailService, EmployeeRepository employeeRepository, MediaRepository mediaRepository, AdCampaignRepository adCampaignRepository, AdCampaignService adCampaignService, StripeAccountRepository stripeAccountRepository, Auth0Service auth0Service) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.reservationRepository = reservationRepository;
        this.emailService = emailService;
        this.employeeRepository = employeeRepository;
        this.mediaRepository = mediaRepository;
        this.adCampaignRepository = adCampaignRepository;
        this.adCampaignService = adCampaignService;
        this.stripeAccountRepository = stripeAccountRepository;
        this.auth0Service = auth0Service;
    }

    @Transactional
    public void handleCheckoutSessionCompleted(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        if (dataObjectDeserializer.getObject().isEmpty()) {
            log.error("Unable to deserialize checkout.session.completed event");
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();
        if (!(stripeObject instanceof Session session)) {
            log.error("Event object is not a Session");
            return;
        }

        String sessionId = session.getId();
        String paymentIntentId = session.getPaymentIntent();

        log.info("Checkout session completed: sessionId={}, paymentIntentId={}",
                sessionId, paymentIntentId);

        // Update our payment record with the PaymentIntent ID
        Optional<PaymentIntent> paymentOpt = paymentIntentRepository.findByStripeSessionId(sessionId);

        if (paymentOpt.isEmpty()) {
            log.warn("No payment record found for session: {}", sessionId);
            return;
        }

        PaymentIntent payment = paymentOpt.get();
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentIntentRepository.save(payment);

        log.info("Updated payment record: reservationId={}, status=SUCCEEDED",
                payment.getReservationId());

        // Update reservation status to CONFIRMED
        updateReservationStatus(payment.getReservationId(), ReservationStatus.CONFIRMED);
    }

    @Transactional
    public void handlePaymentIntentSucceeded(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        if (dataObjectDeserializer.getObject().isEmpty()) {
            log.error("Unable to deserialize payment_intent.succeeded event");
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();
        if (!(stripeObject instanceof com.stripe.model.PaymentIntent stripePaymentIntent)) {
            log.error("Event object is not a PaymentIntent");
            return;
        }

        String paymentIntentId = stripePaymentIntent.getId();

        log.info("Payment intent succeeded: paymentIntentId={}", paymentIntentId);

        // Find and update payment record
        Optional<PaymentIntent> paymentOpt = paymentIntentRepository.findByStripePaymentIntentId(paymentIntentId);

        if (paymentOpt.isPresent()) {
            PaymentIntent payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentIntentRepository.save(payment);

            log.info("Updated payment record from webhook: reservationId={}",
                    payment.getReservationId());

            // Update reservation status to CONFIRMED
            updateReservationStatus(payment.getReservationId(), ReservationStatus.CONFIRMED);
        } else {
            log.warn("No payment record found for PaymentIntent: {}", paymentIntentId);
        }
    }

    @Transactional
    public void handlePaymentIntentFailed(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        if (dataObjectDeserializer.getObject().isEmpty()) {
            log.error("Unable to deserialize payment_intent.payment_failed event");
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();
        if (!(stripeObject instanceof com.stripe.model.PaymentIntent stripePaymentIntent)) {
            log.error("Event object is not a PaymentIntent");
            return;
        }

        String paymentIntentId = stripePaymentIntent.getId();

        log.info("Payment intent failed: paymentIntentId={}", paymentIntentId);

        // Find and update payment record
        Optional<PaymentIntent> paymentOpt = paymentIntentRepository.findByStripePaymentIntentId(paymentIntentId);

        if (paymentOpt.isPresent()) {
            PaymentIntent payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentIntentRepository.save(payment);

            log.info("Marked payment as failed: reservationId={}", payment.getReservationId());

            // Update reservation status to CANCELLED due to payment failure
            updateReservationStatus(payment.getReservationId(), ReservationStatus.CANCELLED);
        } else {
            log.warn("No payment record found for PaymentIntent: {}", paymentIntentId);
        }
    }

    @Transactional
    public void handleAccountUpdated(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isEmpty()) {
            log.error("Unable to deserialize account.updated event");
            return;
        }

        StripeObject stripeObject = dataObjectDeserializer.getObject().get();
        if (!(stripeObject instanceof Account account)) {
            log.error("Event object is not an Account");
            return;
        }

        String stripeAccountId = account.getId();
        log.info("Processing account.updated event for Stripe account: {}", stripeAccountId);

        Optional<StripeAccount> localAccountOpt = stripeAccountRepository.findByStripeAccountId(stripeAccountId);

        if (localAccountOpt.isEmpty()) {
            log.warn("Received account.updated event for an unknown Stripe account: {}", stripeAccountId);
            return;
        }

        StripeAccount localAccount = localAccountOpt.get();
        boolean originalOnboardingComplete = localAccount.isOnboardingComplete();

        // Update our local record with the latest status from Stripe
        localAccount.setOnboardingComplete(Boolean.TRUE.equals(account.getDetailsSubmitted()));
        localAccount.setChargesEnabled(Boolean.TRUE.equals(account.getChargesEnabled()));
        localAccount.setPayoutsEnabled(Boolean.TRUE.equals(account.getPayoutsEnabled()));

        stripeAccountRepository.save(localAccount);

        log.info(
            "Updated local Stripe account {}: onboardingComplete={}, chargesEnabled={}, payoutsEnabled={}",
            stripeAccountId,
            localAccount.isOnboardingComplete(),
            localAccount.isChargesEnabled(),
            localAccount.isPayoutsEnabled()
        );

        if (!originalOnboardingComplete && localAccount.isOnboardingComplete()) {
            log.info("Account {} is now fully onboarded.", stripeAccountId);
        }
    }

    @Transactional
    public void updateReservationStatus(String reservationId, ReservationStatus newStatus) {
        Optional<Reservation> reservationOpt = reservationRepository.findByReservationId(reservationId);

        if (reservationOpt.isEmpty()) {
            log.warn("Reservation not found for payment update: {}", reservationId);
            return;
        }

        Reservation reservation = reservationOpt.get();
        ReservationStatus oldStatus = reservation.getStatus();

        // Only update if status is changing
        if (oldStatus != newStatus) {
            reservation.setStatus(newStatus);
            reservationRepository.save(reservation);
            log.info("Updated reservation status: id={}, {} -> {}",
                    reservationId, oldStatus, newStatus);
            if (newStatus == ReservationStatus.CONFIRMED) {
                Media media = mediaRepository.findById(reservation.getMediaId())
                        .orElseThrow(() -> new MediaNotFoundException(reservation.getMediaId().toString()));
                AdCampaign campaign = adCampaignRepository.findByCampaignId_CampaignId(reservation.getCampaignId());
                if (campaign == null) {
                    throw new AdCampaignNotFoundException(reservation.getCampaignId());
                }
                BigDecimal totalPrice = reservation.getTotalPrice();
                if(totalPrice == null) {
                    totalPrice = BigDecimal.ZERO;
                }
                sendNotificationEmails(media, reservation, campaign, totalPrice);
            }
        } else {
            log.debug("Reservation already has status {}: {}", newStatus, reservationId);
        }
    }

    private void sendNotificationEmails(Media media, Reservation reservation,
                                        AdCampaign campaign, BigDecimal totalPrice) {
        String mediaOwnerBusinessId = media.getBusinessId().toString();
        List<Employee> mediaOwners = employeeRepository.findAllByBusinessId_BusinessId(mediaOwnerBusinessId);
        List<String> mediaOwnerEmailAddresses = mediaOwners.stream()
                .map(Employee::getUserId)
                .filter(uid -> uid != null && !uid.isBlank())
                .distinct()
                .map(uid -> {
                    try {
                        return auth0Service.getUserEmailByUserId(uid);
                    } catch (Exception e) {
                        log.warn("Failed to fetch email for userId {} from Auth0: {}", uid, e.getMessage());
                        return null;
                    }
                })
                .filter(email -> email != null && !email.isEmpty())
                .toList();

        if (!mediaOwnerEmailAddresses.isEmpty()) {
            for (String ownerEmailAddress : mediaOwnerEmailAddresses) {
                sendReservationEmail(ownerEmailAddress, media, reservation, campaign, totalPrice);
            }
        } else {
            log.warn("No email found for media owner in business: {}", mediaOwnerBusinessId);
        }
    }

    private void sendReservationEmail(String ownerEmail, Media media, Reservation reservation,
                                      AdCampaign campaign, BigDecimal totalPrice) {
        try {
            List<String> imageLinks = adCampaignService.getAllCampaignImageLinks(campaign.getCampaignId().getCampaignId());

            String previewSection;
            if (imageLinks == null || imageLinks.isEmpty()) {
                previewSection = "No preview images available.";
            } else {
                StringBuilder sb = new StringBuilder("Preview Images:")
                        .append(System.lineSeparator());
                for (String link : imageLinks) {
                    sb.append("- ")
                            .append(link)
                            .append(System.lineSeparator());
                }
                previewSection = sb.toString().trim();
            }

            String emailBody = String.format(
                    "A new reservation has been created for your media%n" +
                            "Media Name: %s%n" +
                            "Ad Campaign Name: %s%n" +
                            "Total Price: $%.2f%n" +
                            "%s",
                    media.getTitle(), campaign.getName(), totalPrice, previewSection
            );
            emailService.sendSimpleEmail(ownerEmail, "New Reservation Created", emailBody);
        } catch (Exception e) {
            // Log error instead of throwing exception to avoid failing reservation creation
            log.error("Failed to send reservation notification email for reservation: {} to owner: {}",
                    reservation.getReservationId(), ownerEmail, e);
        }
    }
}
