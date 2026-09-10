package com.envisionad.webservice.activecampaign.presentationlayer.models;

import com.envisionad.webservice.advertisement.presentationlayer.models.AdResponseModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    /** When media owners were last told about this business's campaign; null if never. */
    private LocalDateTime lastSwapAt;

    /**
     * Non-null only while the cooldown is actually in force, so the frontend can render its
     * countdown without a second request and without recomputing the window itself.
     */
    private LocalDateTime swapAvailableAt;

    /** True when creatives changed since owners were last notified (FR-8.2). */
    private boolean hasUnnotifiedCreativeChanges;
}
