package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.BundlePayoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Settles media-owner shares held because the owner's Stripe Connect onboarding was incomplete
 * when the invoice was paid (Terms of Service §7): pays those whose owner has since completed it,
 * and expires those still unclaimed after the hold. A one-line delegator, so the logic is tested on
 * {@link BundlePayoutService#releaseOrExpireHeldPayouts()} directly, without waiting on a timer.
 *
 * <p>The {@code account.updated} webhook and the onboarding status read release held shares as
 * soon as they see the account complete; this sweep is the backstop for a missed webhook, and the
 * only thing that expires shares.
 *
 * <p>Behind a property and off in tests, like {@link BundlePayoutRetryJob}: a run in the middle of
 * the suite would touch rows other test classes are asserting on.
 *
 * <p>Runs unlocked, which is safe <strong>only because the backend runs as a single instance</strong>
 * (`envision-docs/docs/architecture/backend.md`, §"Runtime Assumptions"). Even so, the webhook can
 * release the same row at the same moment; the shared idempotency key makes the second transfer
 * request return the first. Add a distributed lock before scaling out.
 */
@Component
@ConditionalOnProperty(name = "envision.payouts.hold.enabled", havingValue = "true", matchIfMissing = true)
public class BundlePayoutHoldJob {

    private final BundlePayoutService bundlePayoutService;

    public BundlePayoutHoldJob(BundlePayoutService bundlePayoutService) {
        this.bundlePayoutService = bundlePayoutService;
    }

    @Scheduled(fixedDelayString = "${envision.payouts.hold.interval}")
    public void releaseOrExpireHeldPayouts() {
        bundlePayoutService.releaseOrExpireHeldPayouts();
    }
}
