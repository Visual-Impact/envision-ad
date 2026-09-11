package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.Ad;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides which creatives each media owner is told to display, from a campaign and the set of
 * screens an advertiser's live subscriptions cover.
 *
 * <p><strong>This implements the predicate pinned in {@code P7-PROGRESS.md}'s handoff section,
 * which supersedes brief 06's FR-4.3/4.5 wherever the two differ</strong> (brief 07 states that
 * precedence explicitly, because brief 06 gets the unclassified-screen case wrong). Restated,
 * per owner business:
 *
 * <ol>
 *   <li>If <em>any</em> of that owner's covered screens has no venue, the owner receives
 *       <em>every</em> creative in the campaign, tagged or not. One unclassified screen makes the
 *       whole owner a send-everything case — this is the rule brief 06 does not handle, and the
 *       reason a screen with no venue must never be silently skipped.</li>
 *   <li>Otherwise the owner receives every creative that carries no venue tags (untagged means
 *       universal, not "for nothing") plus every creative tagged for a venue they actually
 *       operate.</li>
 *   <li>If that leaves nothing, the owner is not contacted at all — an email listing zero
 *       creatives is worse than no email.</li>
 * </ol>
 *
 * <p>Deliberately holds no repositories: it is pure, so the distribution rules can be tested
 * against hand-built {@link Media} and {@link Ad} objects without a database. Callers are
 * responsible for having initialized the lazy {@code campaign.ads} and {@code ad.venues}
 * collections before calling in.
 */
@Component
public class CreativeDistributor {

    /**
     * Groups a campaign's creatives per owner business.
     *
     * @param campaign      the campaign being displayed; its {@code ads} must be initialized
     * @param affectedMedia every screen covered by the advertiser's live subscriptions, across
     *                      all owners, already filtered for business-type exclusions
     * @param ownerOf       resolves a screen to the owner business it should be reported under
     * @param venueLabels   venue id to display name, for section headers; a venue missing from
     *                      the map falls back to its id rather than dropping the section
     * @return one group per owner that has at least one creative to display, in the order the
     *         owners first appear in {@code affectedMedia}
     */
    public List<OwnerCreativeGroup> groupCreativesByOwnerAndVenue(
            AdCampaign campaign,
            List<Media> affectedMedia,
            Function<Media, String> ownerOf,
            Map<String, String> venueLabels) {

        List<Ad> allCreatives = campaign.getAds() == null ? List.of() : List.copyOf(campaign.getAds());
        if (allCreatives.isEmpty() || affectedMedia.isEmpty()) {
            return List.of();
        }

        Map<String, List<Media>> screensByOwner = new LinkedHashMap<>();
        for (Media media : affectedMedia) {
            String ownerBusinessId = ownerOf.apply(media);
            if (ownerBusinessId == null) {
                continue;
            }
            screensByOwner.computeIfAbsent(ownerBusinessId, key -> new ArrayList<>()).add(media);
        }

        List<OwnerCreativeGroup> groups = new ArrayList<>();
        screensByOwner.forEach((ownerBusinessId, screens) -> {
            List<OwnerCreativeGroup.Section> sections = sectionsFor(screens, allCreatives, venueLabels);
            if (!sections.isEmpty()) {
                groups.add(new OwnerCreativeGroup(ownerBusinessId, List.copyOf(screens), sections));
            }
        });
        return groups;
    }

    private List<OwnerCreativeGroup.Section> sectionsFor(
            List<Media> screens, List<Ad> allCreatives, Map<String, String> venueLabels) {

        boolean hasUnclassifiedScreen = screens.stream()
                .anyMatch(media -> media.getVenueId() == null || media.getVenueId().isBlank());
        if (hasUnclassifiedScreen) {
            // Rule 1. No venue split is meaningful once a screen's venue is unknown, so this is a
            // single flat block rather than headers that would imply a precision we don't have.
            return List.of(new OwnerCreativeGroup.Section(null, allCreatives));
        }

        // Insertion-ordered so the email's sections follow the owner's own screen order.
        Set<String> ownerVenueIds = screens.stream()
                .map(Media::getVenueId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<Ad> universal = allCreatives.stream().filter(this::isUntagged).toList();

        Map<String, List<Ad>> taggedByVenue = new LinkedHashMap<>();
        for (String venueId : ownerVenueIds) {
            List<Ad> matching = allCreatives.stream()
                    .filter(ad -> !isUntagged(ad) && venueIdsOf(ad).contains(venueId))
                    .toList();
            if (!matching.isEmpty()) {
                taggedByVenue.put(venueId, matching);
            }
        }

        if (universal.isEmpty() && taggedByVenue.isEmpty()) {
            return List.of(); // Rule 3 — nothing to say, so say nothing.
        }

        // A single venue needs no headers at all (FR-4.6), and neither does a set of creatives
        // that are all universal: in both cases every creative applies to every screen listed.
        if (ownerVenueIds.size() == 1 || taggedByVenue.isEmpty()) {
            List<Ad> flat = new ArrayList<>(universal);
            taggedByVenue.values().forEach(ads -> ads.forEach(ad -> {
                if (!flat.contains(ad)) {
                    flat.add(ad);
                }
            }));
            return List.of(new OwnerCreativeGroup.Section(null, List.copyOf(flat)));
        }

        List<OwnerCreativeGroup.Section> sections = new ArrayList<>();
        if (!universal.isEmpty()) {
            // Untagged creatives belong on every screen this owner has. Listing them once up
            // front is the only way to say that without repeating them under each venue header,
            // which would read as several different instructions instead of one.
            sections.add(new OwnerCreativeGroup.Section(null, universal));
        }
        taggedByVenue.forEach((venueId, ads) ->
                sections.add(new OwnerCreativeGroup.Section(venueLabels.getOrDefault(venueId, venueId), ads)));
        return List.copyOf(sections);
    }

    /**
     * An ad with no venue tags is suitable for every venue. There is no third state — "tagged
     * for zero venues" is indistinguishable from "untagged" by design (see {@code Ad.venues}).
     */
    private boolean isUntagged(Ad ad) {
        return ad.getVenues() == null || ad.getVenues().isEmpty();
    }

    private Set<String> venueIdsOf(Ad ad) {
        return ad.getVenues().stream().map(Venue::getVenueId).collect(LinkedHashSet::new, Set::add, Set::addAll);
    }
}
