package com.envisionad.webservice.proofofdisplay.presentationlayer;

import com.envisionad.webservice.proofofdisplay.businesslogiclayer.ProofOfDisplayReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The jobs only delegate. What matters here is that the two cron expressions fire when FR-4.1 and
 * FR-4.2 say: read off the annotations themselves, so a typo in the code fails this test.
 */
class ProofOfDisplayScheduledJobsUnitTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    @Test
    void eachJobDelegatesToItsReminder() {
        ProofOfDisplayReminderService service = mock(ProofOfDisplayReminderService.class);
        ProofOfDisplayScheduledJobs jobs = new ProofOfDisplayScheduledJobs(service);

        jobs.mediaOwnerReminder();
        jobs.advertiserReminder();

        verify(service).sendMediaOwnerReminders();
        verify(service).sendAdvertiserReminders();
        verifyNoMoreInteractions(service);
    }

    @Test
    void mediaOwnerReminder_firesAt9OnTheLastDayOfEachMonth_includingFebruary() throws Exception {
        CronExpression cron = cronOf("mediaOwnerReminder");

        assertEquals(ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, TORONTO),
                cron.next(ZonedDateTime.of(2026, 10, 12, 18, 0, 0, 0, TORONTO)));
        assertEquals(ZonedDateTime.of(2027, 2, 28, 9, 0, 0, 0, TORONTO),
                cron.next(ZonedDateTime.of(2027, 2, 1, 0, 0, 0, 0, TORONTO)));
        assertEquals(ZonedDateTime.of(2028, 2, 29, 9, 0, 0, 0, TORONTO),
                cron.next(ZonedDateTime.of(2028, 2, 1, 0, 0, 0, 0, TORONTO)));
    }

    @Test
    void advertiserReminder_firesAt9OnTheFirst_theMorningAfterTheOwners() throws Exception {
        CronExpression cron = cronOf("advertiserReminder");

        assertEquals(ZonedDateTime.of(2026, 11, 1, 9, 0, 0, 0, TORONTO),
                cron.next(ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, TORONTO)));
    }

    @Test
    void bothJobsRunOnTorontoTime() throws Exception {
        for (String method : new String[] {"mediaOwnerReminder", "advertiserReminder"}) {
            assertEquals("America/Toronto", scheduled(method).zone(), method);
        }
    }

    private static CronExpression cronOf(String method) throws NoSuchMethodException {
        return CronExpression.parse(scheduled(method).cron());
    }

    private static Scheduled scheduled(String method) throws NoSuchMethodException {
        return ProofOfDisplayScheduledJobs.class.getMethod(method).getAnnotation(Scheduled.class);
    }
}
