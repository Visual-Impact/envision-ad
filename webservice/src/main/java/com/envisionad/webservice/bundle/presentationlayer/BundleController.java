package com.envisionad.webservice.bundle.presentationlayer;

import com.envisionad.webservice.bundle.businesslogiclayer.BundlePriceQuote;
import com.envisionad.webservice.bundle.businesslogiclayer.BundlePricingService;
import com.envisionad.webservice.bundle.businesslogiclayer.BundleService;
import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRuleType;
import com.envisionad.webservice.bundle.mappinglayer.BundleRequestMapper;
import com.envisionad.webservice.bundle.mappinglayer.BundleResponseMapper;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleCandidateMediaResponseModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleRequestModel;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleResponseModel;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public BundleController(BundleService bundleService,
            BundlePricingService pricingService,
            BundleRequestMapper requestMapper,
            BundleResponseMapper responseMapper) {
        this.bundleService = bundleService;
        this.pricingService = pricingService;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    /**
     * Public listing. Prices here are computed with no advertiser context, i.e. what
     * an anonymous browser sees; the buyer-specific quote endpoint arrives with M3.
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

    @PostMapping
    @PreAuthorize("hasAuthority('manage:bundles')")
    public ResponseEntity<BundleResponseModel> createBundle(@RequestBody BundleRequestModel request) {
        Bundle entity = requestMapper.requestModelToEntity(request);
        if (request.getRuleType() == BundleRuleType.FULL_NETWORK) {
            entity.setRuleValue(null);
        }
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
        List<Media> candidates = bundleService.getRuleMatchedMedias(bundle);
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
        BundlePriceQuote quote = pricingService.quote(bundle.getBundleId(), null);
        long activeSubscriptions = bundleService.countBlockingSubscriptions(bundle.getBundleId());
        return responseMapper.entityToResponseModel(bundle, quote, activeSubscriptions);
    }
}
