package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.Ad;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaign;
import com.envisionad.webservice.advertisement.dataaccesslayer.AdIdentifier;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The creative-distribution predicate, tested against hand-built entities — no database, no
 * Spring. The rules under test come from {@code P7-PROGRESS.md}'s handoff section, which
 * overrides brief 06's FR-4.3/4.5.
 */
class CreativeDistributorUnitTest {

    private static final String GYM = "venue-gym";
    private static final String BARBERSHOP = "venue-barbershop";
    private static final String CAFE = "venue-cafe";

    private static final Map<String, String> VENUE_LABELS =
            Map.of(GYM, "Gym", BARBERSHOP, "Barbershop", CAFE, "Cafe");

    private static final String OWNER_A = "owner-a";
    private static final String OWNER_B = "owner-b";

    private final CreativeDistributor distributor = new CreativeDistributor();

    /**
     * Rule 1, and the single most important case here: brief 06 would have this owner receive
     * only the creatives matching their classified screens. It is wrong. One screen whose venue
     * is unknown means we cannot say any creative is inappropriate for it, so the owner gets
     * everything.
     */
    @Test
    void anOwnerWithEvenOneUnclassifiedScreen_receivesEveryCreativeInTheCampaign() {
        Ad gymOnly = ad("Protein Shake", GYM);
        Ad barbershopOnly = ad("Beard Oil", BARBERSHOP);
        AdCampaign campaign = campaign(gymOnly, barbershopOnly);

        List<OwnerCreativeGroup> groups = group(campaign, List.of(
                screen(OWNER_A, GYM),
                screen(OWNER_A, null))); // the unclassified one

        OwnerCreativeGroup ownerA = single(groups);
        assertEquals(List.of(gymOnly, barbershopOnly), ownerA.distinctCreatives(),
                "the barbershop creative must still be sent — the unclassified screen might be one");
        assertEquals(1, ownerA.sections().size(), "an unknown venue makes venue headers meaningless");
        assertNull(ownerA.sections().get(0).venueLabel());
    }

    /** A blank venue id is the same unknown as a null one, not a venue named "". */
    @Test
    void aBlankVenueIdCountsAsUnclassified() {
        Ad gymOnly = ad("Protein Shake", GYM);
        Ad barbershopOnly = ad("Beard Oil", BARBERSHOP);

        List<OwnerCreativeGroup> groups = group(campaign(gymOnly, barbershopOnly), List.of(
                screen(OWNER_A, GYM),
                screen(OWNER_A, "   ")));

        assertEquals(List.of(gymOnly, barbershopOnly), single(groups).distinctCreatives());
    }

    /** Rule 2: an untagged creative is universal, not orphaned. */
    @Test
    void untaggedCreativesGoToEveryOwner() {
        Ad universal = ad("Brand Spot");
        Ad gymOnly = ad("Protein Shake", GYM);

        List<OwnerCreativeGroup> groups = group(campaign(universal, gymOnly), List.of(
                screen(OWNER_A, GYM),
                screen(OWNER_B, BARBERSHOP)));

        assertEquals(List.of(universal, gymOnly), byOwner(groups, OWNER_A).distinctCreatives());
        assertEquals(List.of(universal), byOwner(groups, OWNER_B).distinctCreatives(),
                "owner B has no gym screen, so the gym-only creative is not theirs to display");
    }

    /** Rule 3: no match and nothing universal means no email at all, not an empty one. */
    @Test
    void anOwnerWithNoMatchingCreatives_isDroppedEntirely() {
        List<OwnerCreativeGroup> groups = group(campaign(ad("Protein Shake", GYM)), List.of(
                screen(OWNER_A, GYM),
                screen(OWNER_B, BARBERSHOP)));

        assertEquals(1, groups.size());
        assertEquals(OWNER_A, groups.get(0).ownerBusinessId());
        assertTrue(groups.stream().noneMatch(g -> g.ownerBusinessId().equals(OWNER_B)));
    }

    @Test
    void anOwnerOfSeveralVenues_getsOneSectionPerVenueThatHasTaggedCreatives() {
        Ad gymOnly = ad("Protein Shake", GYM);
        Ad barbershopOnly = ad("Beard Oil", BARBERSHOP);

        List<OwnerCreativeGroup> groups = group(campaign(gymOnly, barbershopOnly), List.of(
                screen(OWNER_A, GYM),
                screen(OWNER_A, BARBERSHOP)));

        List<OwnerCreativeGroup.Section> sections = single(groups).sections();
        assertEquals(List.of("Gym", "Barbershop"), sections.stream().map(OwnerCreativeGroup.Section::venueLabel).toList());
        assertEquals(List.of(gymOnly), sections.get(0).creatives());
        assertEquals(List.of(barbershopOnly), sections.get(1).creatives());
    }

    /**
     * Untagged creatives apply to every one of the owner's screens, so they are stated once
     * rather than repeated beneath each venue header — repeating them would read as several
     * separate instructions rather than one.
     */
    @Test
    void universalCreativesAreListedOnceAheadOfThePerVenueSections() {
        Ad universal = ad("Brand Spot");
        Ad gymOnly = ad("Protein Shake", GYM);
        Ad barbershopOnly = ad("Beard Oil", BARBERSHOP);

        List<OwnerCreativeGroup.Section> sections = single(group(
                campaign(universal, gymOnly, barbershopOnly),
                List.of(screen(OWNER_A, GYM), screen(OWNER_A, BARBERSHOP)))).sections();

        assertEquals(3, sections.size());
        assertNull(sections.get(0).venueLabel(), "the universal block comes first and is unheaded");
        assertEquals(List.of(universal), sections.get(0).creatives());
        assertEquals("Gym", sections.get(1).venueLabel());
        assertEquals("Barbershop", sections.get(2).venueLabel());
    }

    /** FR-4.6: headers only earn their place when there is more than one venue to distinguish. */
    @Test
    void aSingleVenueOwnerGetsAFlatListWithNoHeaders() {
        Ad universal = ad("Brand Spot");
        Ad gymOnly = ad("Protein Shake", GYM);

        List<OwnerCreativeGroup.Section> sections = single(group(
                campaign(universal, gymOnly),
                List.of(screen(OWNER_A, GYM), screen(OWNER_A, GYM)))).sections();

        assertEquals(1, sections.size());
        assertNull(sections.get(0).venueLabel());
        assertEquals(List.of(universal, gymOnly), sections.get(0).creatives());
    }

    /** A venue with no matching creatives contributes no empty header. */
    @Test
    void aVenueWithNoTaggedCreativesProducesNoSection() {
        Ad gymOnly = ad("Protein Shake", GYM);

        List<OwnerCreativeGroup.Section> sections = single(group(
                campaign(gymOnly),
                List.of(screen(OWNER_A, GYM), screen(OWNER_A, CAFE)))).sections();

        assertEquals(1, sections.size());
        assertEquals("Gym", sections.get(0).venueLabel());
    }

    /** A creative tagged for several venues reaches an owner via any one of them. */
    @Test
    void aCreativeTaggedForSeveralVenuesMatchesOnAnyOfThem() {
        Ad gymAndCafe = ad("Smoothie", GYM, CAFE);

        assertEquals(List.of(gymAndCafe),
                single(group(campaign(gymAndCafe), List.of(screen(OWNER_A, CAFE)))).distinctCreatives());
    }

    /** An unlabelled venue must still produce its section, headed by the id rather than vanishing. */
    @Test
    void anUnknownVenueLabelFallsBackToTheVenueId() {
        Ad gymOnly = ad("Protein Shake", GYM);
        Ad mysteryOnly = ad("Mystery", "venue-unlabelled");

        List<OwnerCreativeGroup.Section> sections = distributor.groupCreativesByOwnerAndVenue(
                campaign(gymOnly, mysteryOnly),
                List.of(screen(OWNER_A, GYM), screen(OWNER_A, "venue-unlabelled")),
                media -> OWNER_A,
                Map.of(GYM, "Gym")).get(0).sections();

        assertEquals(List.of("Gym", "venue-unlabelled"),
                sections.stream().map(OwnerCreativeGroup.Section::venueLabel).toList());
    }

    @Test
    void aCampaignWithNoCreatives_producesNoGroups() {
        assertTrue(group(campaign(), List.of(screen(OWNER_A, GYM))).isEmpty());
    }

    @Test
    void noAffectedScreens_producesNoGroups() {
        assertTrue(group(campaign(ad("Brand Spot")), List.of()).isEmpty());
    }

    /** A screen we cannot attribute to an owner is skipped rather than grouped under null. */
    @Test
    void aScreenWithNoResolvableOwner_isSkipped() {
        Media orphan = screen(null, GYM);
        List<OwnerCreativeGroup> groups = distributor.groupCreativesByOwnerAndVenue(
                campaign(ad("Brand Spot")),
                List.of(orphan, screen(OWNER_A, GYM)),
                media -> media == orphan ? null : OWNER_A,
                VENUE_LABELS);

        assertEquals(1, groups.size());
        assertEquals(OWNER_A, groups.get(0).ownerBusinessId());
    }

    @Test
    void eachOwnerSeesOnlyTheirOwnScreens() {
        List<OwnerCreativeGroup> groups = group(campaign(ad("Brand Spot")), List.of(
                screen(OWNER_A, GYM), screen(OWNER_B, GYM), screen(OWNER_A, GYM)));

        assertEquals(2, byOwner(groups, OWNER_A).screens().size());
        assertEquals(1, byOwner(groups, OWNER_B).screens().size());
    }

    // ---------- helpers ----------

    private List<OwnerCreativeGroup> group(AdCampaign campaign, List<Media> media) {
        Function<Media, String> ownerOf = m -> m.getBusinessId() == null ? null : ownerNames.get(m.getBusinessId());
        return distributor.groupCreativesByOwnerAndVenue(campaign, media, ownerOf, VENUE_LABELS);
    }

    private final java.util.Map<UUID, String> ownerNames = new java.util.HashMap<>();

    private Media screen(String ownerBusinessId, String venueId) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setTitle("Screen " + media.getId());
        media.setVenueId(venueId);
        if (ownerBusinessId != null) {
            UUID ownerUuid = ownerUuids.computeIfAbsent(ownerBusinessId, key -> UUID.randomUUID());
            media.setBusinessId(ownerUuid);
            ownerNames.put(ownerUuid, ownerBusinessId);
        }
        return media;
    }

    private final java.util.Map<String, UUID> ownerUuids = new java.util.HashMap<>();

    private AdCampaign campaign(Ad... ads) {
        AdCampaign campaign = new AdCampaign();
        campaign.setName("Summer Sale");
        campaign.setAds(List.of(ads));
        return campaign;
    }

    private Ad ad(String name, String... venueIds) {
        Ad ad = new Ad();
        ad.setAdIdentifier(new AdIdentifier());
        ad.setName(name);
        ad.setAdUrl("https://cdn.example.com/" + name.replace(' ', '-') + ".jpg");
        ad.setVenues(java.util.Arrays.stream(venueIds).map(id -> {
            Venue venue = new Venue();
            venue.setVenueId(id);
            return venue;
        }).toList());
        return ad;
    }

    private OwnerCreativeGroup single(List<OwnerCreativeGroup> groups) {
        assertEquals(1, groups.size(), "expected exactly one owner group");
        return groups.get(0);
    }

    private OwnerCreativeGroup byOwner(List<OwnerCreativeGroup> groups, String ownerBusinessId) {
        return groups.stream()
                .filter(group -> group.ownerBusinessId().equals(ownerBusinessId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no group for owner " + ownerBusinessId));
    }
}
