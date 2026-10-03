package com.envisionad.webservice.bundle.presentationlayer;

import com.envisionad.webservice.bundle.businesslogiclayer.BundlePriceQuote;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePricingService;
import com.envisionad.webservice.bundle.businesslogiclayer.BundleService;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import com.envisionad.webservice.bundle.mappinglayer.BundleRequestMapper;
import com.envisionad.webservice.bundle.mappinglayer.BundleResponseMapper;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleCandidateMediaResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundlePriceQuoteResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleRequestModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleResponseModel;
import com.envisionad.webservice.bundle.exceptions.NotAdvertiserException;
import com.envisionad.webservice.business.businesslogiclayer.BusinessService;
import com.envisionad.webservice.business.dataaccesslayer.Roles;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.utils.JwtUtils;
import com.envisionad.webservice.venue.businesslogiclayer.VenueService;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bundles")
@CrossOrigin(origins = {"http://localhost:3000", "https://envision-ad.ca"})
public class BundleController {

    private final BundleService bundleService;
    private final BundlePricingService pricingService;
    private final BundleRequestMapper requestMapper;
    private final BundleResponseMapper responseMapper;
    private final VenueService venueService;
    private final JwtUtils jwtUtils;
    private final BusinessService businessService;

    public BundleController(BundleService bundleService,
            BundlePricingService pricingService,
            BundleRequestMapper requestMapper,
            BundleResponseMapper responseMapper,
            VenueService venueService,
            JwtUtils jwtUtils,
            BusinessService businessService) {
        this.bundleService = bundleService;
        this.pricingService = pricingService;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.venueService = venueService;
        this.jwtUtils = jwtUtils;
        this.businessService = businessService;
    }

    /**
     * Public listing. Prices here are computed with no advertiser context, i.e. what
     * an anonymous browser sees; {@code /quotes} gives a signed-in advertiser theirs.
     */
    @GetMapping
    public ResponseEntity<List<BundleResponseModel>> getAllBundles(
            @RequestParam(required = false) BundleRuleType ruleType,
            @RequestParam(required = false, defaultValue = "true") boolean active) {
        List<BundleResponseModel> response = bundleService.getAllBundles(ruleType, active).stream()
                .map(this::toResponseModel)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bundleId}")
    public ResponseEntity<BundleResponseModel> getBundleByBundleId(@PathVariable String bundleId) {
        return ResponseEntity.ok(toResponseModel(bundleService.getBundleByBundleId(bundleId)));
    }

    /**
     * Buyer-specific price preview, called by the subscribe flow (M4). It differs from the
     * public price when the buyer has a business type (P4): screens in that venue are
     * dropped, and {@code excludedScreenCount} says how many.
     */
    @GetMapping("/{bundleId}/quote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BundlePriceQuoteResponseModel> getBundleQuote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String bundleId,
            @RequestParam String businessId) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);
        requireAdvertiser(businessId);
        // Resolves the bundle first, so an unknown id yields 404 rather than an empty quote.
        Bundle bundle = bundleService.getBundleByBundleId(bundleId);
        return ResponseEntity.ok(toQuoteResponseModel(bundle, businessId));
    }

    /**
     * The buyer quote for every active bundle at once, so the home page's bundle cards can show
     * the price this advertiser would actually pay without one request per card. Same bundle set
     * and same checks as {@link #getAllBundles} and {@link #getBundleQuote}.
     */
    @GetMapping("/quotes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BundlePriceQuoteResponseModel>> getBundleQuotes(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String businessId) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);
        requireAdvertiser(businessId);
        List<BundlePriceQuoteResponseModel> response = bundleService.getAllBundles(null, true).stream()
                .map(bundle -> toQuoteResponseModel(bundle, businessId))
                .toList();
        return ResponseEntity.ok(response);
    }

    private BundlePriceQuoteResponseModel toQuoteResponseModel(Bundle bundle, String businessId) {
        BundlePriceQuote quote = pricingService.quote(bundle, businessId);
        // Quoted again with no buyer, exactly as the public listing does, so the UI can say how
        // many of the screens on the card this buyer won't get. Buyer-independent filters
        // (inactive, manual exclusion, P8's sold-out) apply to both, so only P4 shows up here.
        BundlePriceQuote publicQuote = pricingService.quote(bundle, null);
        return responseMapper.quoteToResponseModel(bundle.getBundleId(), quote, publicQuote);
    }

    /** Bundles are advertiser inventory; a media-owner-only business can browse but not buy. */
    private void requireAdvertiser(String businessId) {
        Roles roles = businessService.getBusinessById(businessId).getRoles();
        if (roles == null || !roles.isAdvertiser()) {
            throw new NotAdvertiserException(businessId);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage:bundles')")
    public ResponseEntity<BundleResponseModel> createBundle(@RequestBody BundleRequestModel request) {
        Bundle entity = requestMapper.requestModelToEntity(request);
        Bundle saved = bundleService.createBundle(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseModel(saved));
    }

    @PutMapping("/{bundleId}")
    @PreAuthorize("hasAuthority('manage:bundles')")
    public ResponseEntity<BundleResponseModel> updateBundle(
            @PathVariable String bundleId,
            @RequestBody BundleRequestModel request) {
        return ResponseEntity.ok(toResponseModel(bundleService.updateBundle(bundleId, request)));
    }

    @DeleteMapping("/{bundleId}")
    @PreAuthorize("hasAuthority('manage:bundles')")
    public ResponseEntity<Void> deleteBundle(@PathVariable String bundleId) {
        bundleService.deleteBundle(bundleId);
        return ResponseEntity.noContent().build();
    }

    /** The rule-matched set before exclusions, each row flagged with its exclusion state. */
    @GetMapping("/{bundleId}/candidate-medias")
    @PreAuthorize("hasAuthority('manage:bundles')")
    public ResponseEntity<List<BundleCandidateMediaResponseModel>> getCandidateMedias(
            @PathVariable String bundleId) {
        Bundle bundle = bundleService.getBundleByBundleId(bundleId);
        List<Media> candidates = bundleService.getCandidateMedias(bundle);
        Set<UUID> excluded = bundleService.getExcludedMediaIds(bundleId);
        return ResponseEntity.ok(
                responseMapper.mediaListToCandidateResponseModelList(candidates, excluded));
    }

    @PutMapping("/{bundleId}/excluded-medias/{mediaId}")
    @PreAuthorize("hasAuthority('manage:bundles')")
    public ResponseEntity<Void> excludeMedia(@PathVariable String bundleId, @PathVariable UUID mediaId) {
        bundleService.excludeMedia(bundleId, mediaId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{bundleId}/excluded-medias/{mediaId}")
    @PreAuthorize("hasAuthority('manage:bundles')")
    public ResponseEntity<Void> includeMedia(@PathVariable String bundleId, @PathVariable UUID mediaId) {
        bundleService.includeMedia(bundleId, mediaId);
        return ResponseEntity.noContent().build();
    }

    private BundleResponseModel toResponseModel(Bundle bundle) {
        BundlePriceQuote quote = pricingService.quote(bundle, null);
        long activeSubscriptions = bundleService.countBlockingSubscriptions(bundle.getBundleId());
        return responseMapper.entityToResponseModel(bundle, quote, activeSubscriptions,
                ruleVenue(bundle));
    }

    /**
     * The venue behind a VENUE-rule bundle, so the response can carry a name instead
     * of the raw {@code venue_id} the rule stores. Null for every other rule type and
     * for a venue that has since been deleted; the mapper falls back to the raw value.
     */
    private Venue ruleVenue(Bundle bundle) {
        if (bundle.getRuleType() != BundleRuleType.VENUE) {
            return null;
        }
        return venueService.findVenueByVenueId(bundle.getRuleValue()).orElse(null);
    }
}
