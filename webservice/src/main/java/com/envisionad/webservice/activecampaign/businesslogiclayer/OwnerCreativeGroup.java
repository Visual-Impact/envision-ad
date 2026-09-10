package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.Ad;
import com.envisionad.webservice.media.DataAccessLayer.Media;

import java.util.List;

/**
 * What one media-owner business has to be told: which of their screens this advertiser's
 * campaign covers, and which creatives belong on them, already split into the sections the
 * email renders.
 *
 * @param ownerBusinessId the owner business, as {@code business.business_id}
 * @param screens         that owner's screens covered by the advertiser's live subscriptions
 * @param sections        creatives to display, in render order; never empty (an owner with
 *                        nothing to show is dropped before a group is built for them)
 */
public record OwnerCreativeGroup(String ownerBusinessId, List<Media> screens, List<Section> sections) {

    /**
     * One block of the email. A null {@code venueLabel} means "these apply to all of your
     * screens" — either because the owner has a single venue type (so per-venue headers would
     * be noise), because a screen of theirs is unclassified, or because the creatives in it
     * carry no venue tags at all and are therefore universal.
     */
    public record Section(String venueLabel, List<Ad> creatives) {}

    /** Every creative across every section, de-duplicated, in render order. */
    public List<Ad> distinctCreatives() {
        return sections.stream().flatMap(section -> section.creatives().stream()).distinct().toList();
    }
}
