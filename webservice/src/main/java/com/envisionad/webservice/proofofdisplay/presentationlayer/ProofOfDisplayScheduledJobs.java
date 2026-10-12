package com.envisionad.webservice.proofofdisplay.presentationlayer;

import com.envisionad.webservice.proofofdisplay.businesslogiclayer.ProofOfDisplayReminderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two monthly proof-of-display reminders (P12 FR-4.4). One-line delegators, so the logic is
 * tested on {@link ProofOfDisplayReminderService} directly, without waiting for a calendar date.
 *
 * <p>Media owners get theirs on the last day of the month ({@code L}), advertisers the next
 * morning, so owners have a day to update their page before advertisers are told to look.
 *
 * <p>Behind a property and off in tests, for the same reason as {@code CreativeChangeNotificationSweep}:
 * {@code BaseIntegrationTest} shares one mocked {@code EmailService}, and a job firing mid-run
 * (a test suite running at 09:00 on the 1st) would pollute it.
 *
 * <p>Runs unlocked, which is safe <strong>only because the backend runs as a single instance</strong>
 * (`envision-docs/docs/architecture/backend.md`, §"Runtime Assumptions"). On more than one
 * instance, every instance would send every reminder: add a distributed lock (ShedLock or a
 * Postgres advisory lock) first. A restart at 09:00 could in theory double-send; accepted for jobs
 * that fire twelve times a year (FR-4.5).
 */
@Component
@ConditionalOnProperty(name = "envision.proof-of-display.reminders.enabled", havingValue = "true",
        matchIfMissing = true)
public class ProofOfDisplayScheduledJobs {

    private final ProofOfDisplayReminderService reminderService;

    public ProofOfDisplayScheduledJobs(ProofOfDisplayReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "0 0 9 L * *", zone = "America/Toronto")
    public void mediaOwnerReminder() {
        reminderService.sendMediaOwnerReminders();
    }

    @Scheduled(cron = "0 0 9 1 * *", zone = "America/Toronto")
    public void advertiserReminder() {
        reminderService.sendAdvertiserReminders();
    }
}
