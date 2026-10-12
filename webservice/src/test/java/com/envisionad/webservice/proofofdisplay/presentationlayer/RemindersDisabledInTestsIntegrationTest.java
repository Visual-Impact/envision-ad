package com.envisionad.webservice.proofofdisplay.presentationlayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Same guard as P6's {@code SweepDisabledInTestsIntegrationTest}: a reminder job firing during a
 * test run (a suite running at 09:00 on the 1st) would send mocked emails into the shared
 * {@code EmailService} mock and break unrelated tests intermittently.
 */
class RemindersDisabledInTestsIntegrationTest extends BaseIntegrationTest {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void theReminderJobsAreNotRegisteredUnderTheTestProfile() {
        assertEquals(0,
                applicationContext.getBeanNamesForType(ProofOfDisplayScheduledJobs.class).length,
                "envision.proof-of-display.reminders.enabled must stay false in src/test/resources/application.yml");
    }
}
