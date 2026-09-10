package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.activecampaign.presentationlayer.models.ActiveCampaignSummaryModel;
import com.envisionad.webservice.activecampaign.presentationlayer.models.NotificationResultModel;
import com.envisionad.webservice.advertisement.presentationlayer.models.AdCampaignResponseModel;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * The one campaign an advertiser business is currently displaying across every screen its live
 * bundle subscriptions cover, and the machinery for changing it.
 *
 * <p>There is no automatic publishing anywhere in this platform — media owners update physical
 * screens by hand. Everything here exists so the right owner is told the right thing at the
 * right time when what should be on their screens changes.
 */
public interface ActiveCampaignService {

    /** The dashboard panel's data, or null when the business has no active campaign set. */
    ActiveCampaignSummaryModel getActiveCampaignSummary(String businessId);

    /**
     * The first, mandatory pick — made during checkout, before a business has ever displayed
     * anything. Sends no email and is not rate-limited: at this instant the screens are only
     * just becoming subscribed, so there is no change for an owner to act on.
     *
     * @throws com.envisionad.webservice.activecampaign.exceptions.ActiveCampaignAlreadySetException
     *         if one is already set — changing what is on screen is the swap endpoint's job
     */
    ActiveCampaignSummaryModel selectInitialActiveCampaign(String businessId, String campaignId);

    /**
     * Changes what is displayed and tells every affected media owner what to put up instead.
     * Selecting the campaign that is already active is a no-op: no email, no event row, no
     * cooldown consumed.
     */
    ActiveCampaignSummaryModel swapActiveCampaign(Jwt jwt, String businessId, String campaignId);

    /** Re-sends the current campaign's creatives without changing which campaign is active. */
    NotificationResultModel notifyMediaOwners(Jwt jwt, String businessId);

    /**
     * Sends the automatic notification if — and only if — this business's active campaign still
     * has creative changes that owners have not been told about (FR-8.2). Re-checking here rather
     * than trusting the sweep's query is what makes the sweep safe to re-run: a manual notify
     * that landed in between simply makes this a no-op.
     *
     * <p>Deliberately exempt from the swap/notify cooldown (FR-8.5). That cooldown exists to stop
     * a person hammering a button; this is already rate-limited far more strictly by its own
     * quiet period, and letting a recent manual action suppress it would reopen the very gap it
     * closes.
     *
     * @return true if an email run actually happened
     */
    boolean autoNotifyIfCreativeChangesArePending(String businessId);

    /**
     * The business's campaigns that could go on screen — at least one creative, and not the one
     * already displayed.
     */
    List<AdCampaignResponseModel> getCampaignsEligibleForSwap(String businessId);
}
