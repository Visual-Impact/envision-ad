package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.BundlePayoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries media-owner payouts that failed only because the platform's available balance could
 * not cover them yet. A one-line delegator, so the logic is tested on
 * {@link BundlePayoutService#retryInsufficientFundsPayouts()} directly, without waiting on a timer.
 *
 * <p>Runs hourly, but each payout is retried at most once a day: Stripe replays the cached error
 * for a reused idempotency key for about 24 hours, so an earlier retry could only fail again.
 *
 * <p>Behind a property and off in tests, like {@code ProofOfDisplayScheduledJobs}: a run in the
 * middle of the suite would touch rows other test classes are asserting on.
 *
 * <p>Runs unlocked, which is safe <strong>only because the backend runs as a single instance</strong>
 * (`envision-docs/docs/architecture/backend.md`, §"Runtime Assumptions"). With more than one
 * instance, two could retry the same row at once; the idempotency key would still stop a second
 * transfer, but add a distributed lock (ShedLock or a Postgres advisory lock) before scaling out.
 */
@Component
@ConditionalOnProperty(name = "envision.payouts.retry.enabled", havingValue = "true", matchIfMissing = true)
public class BundlePayoutRetryJob {

    private final BundlePayoutService bundlePayoutService;

    public BundlePayoutRetryJob(BundlePayoutService bundlePayoutService) {
        this.bundlePayoutService = bundlePayoutService;
    }

    @Scheduled(fixedDelayString = "${envision.payouts.retry.interval}")
    public void retryInsufficientFundsPayouts() {
        bundlePayoutService.retryInsufficientFundsPayouts();
    }
}
