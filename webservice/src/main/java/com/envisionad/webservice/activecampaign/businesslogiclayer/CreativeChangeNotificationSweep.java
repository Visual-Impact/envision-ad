package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tells media owners about creative changes the advertiser never got round to announcing.
 *
 * <p><strong>Why this exists.</strong> Advertisers on a monthly subscription may change the
 * creatives on a running campaign at will (P1 M6, decision D42). Nothing tells the media owners,
 * who load creatives onto screens by hand — so an advertiser can retire every creative in their
 * campaign and the screens keep showing the old ones indefinitely. The dashboard prompts and the
 * manual notify button help, but they depend on somebody remembering. This does not.
 *
 * <p><strong>Why it batches rather than sending per edit.</strong> Creatives are added one at a
 * time, so emailing on each would send five messages to every owner while an advertiser
 * assembles one campaign — worse noise than the problem it solves. The quiet period restarts on
 * every edit, so assembling a campaign in one sitting produces exactly one email, an hour after
 * the last change.
 *
 * <p><strong>Why a manual notify silences it.</strong> Nothing here cancels anything explicitly:
 * a manual notify writes an event row, which makes the campaign's creatives no longer newer than
 * its last notification, so the pending condition is simply false the next time this runs.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "envision.active-campaign.sweep.enabled", havingValue = "true",
        matchIfMissing = true)
public class CreativeChangeNotificationSweep {

    private final BusinessRepository businessRepository;
    private final ActiveCampaignService activeCampaignService;

    /**
     * How long creative changes must sit untouched before owners are told on the advertiser's
     * behalf. Long enough that building a campaign in one sitting is a single email; short
     * enough that a screen is never showing retired creative for a whole business day.
     */
    private final Duration quietPeriod;

    public CreativeChangeNotificationSweep(
            BusinessRepository businessRepository,
            ActiveCampaignService activeCampaignService,
            @Value("${envision.active-campaign.notify-quiet-period}") Duration quietPeriod) {
        this.businessRepository = businessRepository;
        this.activeCampaignService = activeCampaignService;
        this.quietPeriod = quietPeriod;
    }

    /**
     * Runs on a plain unlocked schedule, which is safe <strong>only because the backend runs as a
     * single instance</strong> — see `envision-docs/docs/architecture/backend.md`,
     * §"Runtime Assumptions". If this platform is ever scaled to more than one backend process,
     * this method must gain a distributed lock (ShedLock on a database table, a Postgres advisory
     * lock, or claiming each business's row with a conditional update before emailing) before
     * that happens. Without one, every instance would sweep the same businesses and each media
     * owner would receive one duplicate email per instance — precisely the noise the batching
     * above exists to avoid.
     *
     * <p>Transactional because it walks the lazy {@code campaign.ads} and {@code ad.venues}
     * collections while composing each email.
     *
     * <p>Runs more often than the quiet period so a batch waits no longer than it has to; the
     * query is indexed on {@code creatives_updated_at} and returns nothing in the ordinary case.
     */
    @Transactional
    @Scheduled(fixedDelayString = "${envision.active-campaign.sweep.interval}")
    public void notifyOwnersOfUnannouncedCreativeChanges() {
        LocalDateTime changedBefore = LocalDateTime.now().minus(quietPeriod);
        List<Business> candidates = businessRepository.findBusinessesWithCreativeChangesOlderThan(
                changedBefore, BundleSubscriptionStatus.LIVE);
        if (candidates.isEmpty()) {
            return;
        }

        for (Business business : candidates) {
            String businessId = business.getBusinessId().getBusinessId();
            try {
                // One business's failure — an unreachable Auth0, a campaign deleted mid-sweep —
                // must not strand every business queued behind it until the next run.
                activeCampaignService.autoNotifyIfCreativeChangesArePending(businessId);
            } catch (Exception e) {
                log.error("Automatic creative-change notification failed for business {}", businessId, e);
            }
        }
    }
}
