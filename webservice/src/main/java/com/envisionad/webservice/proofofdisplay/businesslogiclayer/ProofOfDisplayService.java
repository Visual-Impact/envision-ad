package com.envisionad.webservice.proofofdisplay.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.proofofdisplay.exceptions.MediaNotInActiveSubscriptionException;
import com.envisionad.webservice.utils.JwtUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.advertisement.exceptions.AdCampaignNotFoundException;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.exceptions.MediaNotFoundException;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.proofofdisplay.exceptions.AdvertiserEmailNotFoundException;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.ProofOfDisplayRequest;
import com.envisionad.webservice.utils.EmailService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ProofOfDisplayService {

    private final EmailService emailService;
    private final EmployeeRepository employeeRepository;
    private final MediaRepository mediaRepository;
    private final AdCampaignRepository adCampaignRepository;
    private final JwtUtils jwtUtils;
    private final BundleSubscriptionItemRepository bundleSubscriptionItemRepository;
    private final Auth0Service auth0Service;

    /**
     * A past-due subscriber is still owed proof of display for the cycle they are in — their
     * screens keep running until the subscription is actually cancelled (decision D40).
     */
    private static final List<BundleSubscriptionStatus> LIVE_SUBSCRIPTION_STATUSES =
            List.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE);

    public ProofOfDisplayService(
            EmailService emailService,
            EmployeeRepository employeeRepository,
            MediaRepository mediaRepository,
            AdCampaignRepository adCampaignRepository,
            JwtUtils jwtUtils,
            BundleSubscriptionItemRepository bundleSubscriptionItemRepository,
            Auth0Service auth0Service
    ) {
        this.emailService = emailService;
        this.employeeRepository = employeeRepository;
        this.mediaRepository = mediaRepository;
        this.adCampaignRepository = adCampaignRepository;
        this.jwtUtils = jwtUtils;
        this.bundleSubscriptionItemRepository = bundleSubscriptionItemRepository;
        this.auth0Service = auth0Service;
    }

    public void sendProofEmail(Jwt jwt, ProofOfDisplayRequest request) {

        if (jwt == null || jwt.getSubject() == null) {
                throw new SecurityException("Invalid JWT token or subject");
            }
            // Proof images are required
            List<String> urls = request.getProofImageUrls();
            if (urls == null || urls.isEmpty()) {
                throw new IllegalArgumentException("At least one proof image URL is required.");
            }

            String userId = jwtUtils.extractUserId(jwt);

            UUID mediaId = UUID.fromString(request.getMediaId());
            String campaignId = request.getCampaignId();

            // Validate / fetch media (with location eagerly fetched for the email body below)
            Media media = mediaRepository.findAllByIdWithLocation(List.of(mediaId)).stream()
                    .findFirst()
                    .orElseThrow(() -> new MediaNotFoundException(request.getMediaId()));

            // Validate / fetch campaign
            AdCampaign campaign = adCampaignRepository.findByCampaignId_CampaignId(campaignId);
            if (campaign == null) {
                throw new AdCampaignNotFoundException(campaignId);
            }

            // AUTHORIZATION — user must belong to the media owner business
            if (media.getBusinessId() == null) {
                throw new IllegalStateException("Media has no associated business");
            }

            String mediaOwnerBusinessId = media.getBusinessId().toString();
            jwtUtils.validateUserIsEmployeeOfBusiness(userId, mediaOwnerBusinessId);


            // The campaign must actually be running on this screen: it must hold a live bundle
            // subscription whose locked item set includes this media (M6, decision D40 — this
            // replaced the CONFIRMED/PENDING reservation check when reservations were retired).
            boolean campaignRunsOnThisMedia =
                    bundleSubscriptionItemRepository.existsForMediaAndCampaignWithSubscriptionStatusIn(
                            mediaId, campaignId, LIVE_SUBSCRIPTION_STATUSES);

            if (!campaignRunsOnThisMedia) {
                throw new MediaNotInActiveSubscriptionException(mediaId.toString(), campaignId);
            }

            // Resolve advertiser email via Auth0 Management API (always up-to-date)
            String advertiserBusinessId = campaign.getBusinessId().getBusinessId();

            List<Employee> advertiserEmployees =
                    employeeRepository.findAllByBusinessId_BusinessId(advertiserBusinessId);

            String advertiserEmail = advertiserEmployees.stream()
                    .map(Employee::getUserId)
                    .filter(uid -> uid != null && !uid.isBlank())
                    .findFirst()
                    .map(auth0Service::getUserEmailByUserId)
                    .orElseThrow(() -> new AdvertiserEmailNotFoundException(advertiserBusinessId));

            //  Build email body
            String subject = "Your ad is live!";

            StringBuilder body = new StringBuilder();
            body.append("Hi there,\n\n");
            body.append("Great news, your ad has been displayed!\n\n");
            body.append("Campaign: ").append(campaign.getName()).append("\n");
            body.append("Media location: ").append(media.getTitle());
            if (media.getMediaLocation() != null) {
                body.append(" (")
                        .append(media.getMediaLocation().getName())
                        .append(", ")
                        .append(media.getMediaLocation().getCity())
                        .append(")");
            }
            body.append("\n");
            body.append("Submitted: ")
                    .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")))
                    .append("\n\n");

            body.append("Proof images:\n");
            for (String url : urls) {
                body.append("- ").append(url).append("\n");
            }
            body.append("\nThanks for advertising with Envision Ad!\n");
            body.append("— The Envision Ad Team");

            emailService.sendSimpleEmail(advertiserEmail, subject, body.toString());
    }
}
