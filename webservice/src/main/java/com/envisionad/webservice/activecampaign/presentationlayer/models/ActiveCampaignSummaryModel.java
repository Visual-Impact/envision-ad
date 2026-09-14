package com.envisionad.webservice.activecampaign.presentationlayer.models;

import com.envisionad.webservice.advertisement.presentationlayer.models.AdResponseModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * What the advertiser's "Currently Displaying" panel needs, in one round trip.
 *
 * <p>{@code subscribedBundleCount} and {@code subscribedScreenCount} are real counts, never
 * placeholders: brief 06 allowed for stubbing them null while P1 was unbuilt, but P1 has
 * shipped, so a null here would mean a genuine fault rather than "not wired up yet".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveCampaignSummaryModel {

    private String campaignId;
    private String name;
    private List<AdResponseModel> ads;
    private int subscribedBundleCount;
    private int subscribedScreenCount;

    /**
     * When a person last told media owners about this business's campaign (a swap or a manual
     * notify); null if never.
     *
     * <p>All three timestamps here carry an offset. Event times are stored zone-less in the JVM's
     * zone — UTC in the production container — so without one, a browser in Montreal would read
     * them as local time and render a cooldown hours too long.
     */
    private OffsetDateTime lastSwapAt;

    /**
     * Non-null only while the cooldown is actually in force, so the frontend can render its
     * countdown without a second request and without recomputing the window itself.
     */
    private OffsetDateTime swapAvailableAt;
    /**
     * When the automatic sweep last emailed owners about this campaign on the advertiser's behalf;
     * null if never. Lets the dashboard say so, so an email sent in their name is never a surprise.
     */
    private OffsetDateTime lastAutoNotifiedAt;

    /** True when creatives changed since owners were last notified (FR-8.2). */
    private boolean hasUnnotifiedCreativeChanges;
}
