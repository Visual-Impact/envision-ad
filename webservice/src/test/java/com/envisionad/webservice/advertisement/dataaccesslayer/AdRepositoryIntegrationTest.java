package com.envisionad.webservice.advertisement.dataaccesslayer;

import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import com.envisionad.webservice.venue.dataaccesslayer.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two derived queries P7 adds to AdRepository. Both traverse the new
 * @ManyToMany — the first such relationship in the codebase — and a malformed property
 * path in either would fail at Spring context startup rather than at call time.
 */
class AdRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AdRepository adRepository;

    @Autowired
    private AdCampaignRepository adCampaignRepository;

    @Autowired
    private VenueRepository venueRepository;

    private Venue gym;
    private Venue barbershop;

    @BeforeEach
    void setUp() {
        adCampaignRepository.deleteAll();
        venueRepository.deleteAll();

        gym = persistVenue("Gym", "Gymnase");
        barbershop = persistVenue("Barbershop", "Salon de coiffure");
    }

    @Test
    void countByVenues_VenueId_countsOnlyAdsTaggedWithThatVenue() {
        persistCampaignWithAds(
                adTaggedWith("Gym A", List.of(gym)),
                adTaggedWith("Gym B", List.of(gym)),
                adTaggedWith("Barber A", List.of(barbershop)),
                adTaggedWith("Universal", List.of()));

        assertEquals(2, adRepository.countByVenues_VenueId(gym.getVenueId()));
        assertEquals(1, adRepository.countByVenues_VenueId(barbershop.getVenueId()));
    }

    @Test
    void countByVenues_VenueId_unknownVenue_returnsZero() {
        persistCampaignWithAds(adTaggedWith("Gym A", List.of(gym)));

        assertEquals(0, adRepository.countByVenues_VenueId("no-such-venue"));
    }

    @Test
    void findByVenues_VenueId_returnsTaggedAds() {
        persistCampaignWithAds(
                adTaggedWith("Gym A", List.of(gym)),
                adTaggedWith("Universal", List.of()));

        List<Ad> found = adRepository.findByVenues_VenueId(gym.getVenueId());

        assertEquals(1, found.size());
        assertEquals("Gym A", found.get(0).getName());
    }

    @Test
    void findByVenues_VenueId_multiTaggedAd_appearsForEachOfItsVenues() {
        persistCampaignWithAds(adTaggedWith("Both", List.of(gym, barbershop)));

        assertEquals(1, adRepository.findByVenues_VenueId(gym.getVenueId()).size());
        assertEquals(1, adRepository.findByVenues_VenueId(barbershop.getVenueId()).size());
    }

    @Test
    void findByVenues_VenueId_untaggedAdsAreNeverReturned() {
        persistCampaignWithAds(adTaggedWith("Universal", List.of()));

        assertTrue(adRepository.findByVenues_VenueId(gym.getVenueId()).isEmpty());
    }

    // ---------------- Helpers ----------------

    private Venue persistVenue(String nameEn, String nameFr) {
        Venue venue = new Venue();
        venue.setVenueId(UUID.randomUUID().toString());
        venue.setNameEn(nameEn);
        venue.setNameFr(nameFr);
        venue.setColorCode("#FF5733");
        return venueRepository.save(venue);
    }

    private static Ad adTaggedWith(String name, List<Venue> venues) {
        Ad ad = new Ad();
        ad.setAdIdentifier(new AdIdentifier());
        ad.setName(name);
        ad.setAdUrl("https://cdn.envisionad.com/" + name.replace(' ', '-') + ".jpg");
        ad.setAdType(AdType.IMAGE);
        ad.setVenues(new ArrayList<>(venues));
        return ad;
    }

    private void persistCampaignWithAds(Ad... ads) {
        AdCampaign campaign = new AdCampaign();
        campaign.setName("Repo Test Campaign");
        campaign.setCampaignId(new AdCampaignIdentifier());
        campaign.setBusinessId(new BusinessIdentifier(UUID.randomUUID().toString()));

        for (Ad ad : ads) {
            ad.setCampaign(campaign);
        }
        campaign.setAds(new ArrayList<>(List.of(ads)));

        adCampaignRepository.save(campaign);
    }
}
