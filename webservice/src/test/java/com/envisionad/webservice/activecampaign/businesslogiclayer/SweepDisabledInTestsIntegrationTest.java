package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the reason the sweep is behind a property at all.
 *
 * <p>{@code BaseIntegrationTest} shares a single Spring context, with {@code EmailService} mocked
 * once for every test class in it. A live scheduler firing the sweep partway through an unrelated
 * class would send real (mocked) emails into that shared mock and break
 * {@code verify(emailService, ...)} somewhere else entirely — an intermittent failure in a test
 * that has nothing to do with P6, which is close to the worst kind to debug.
 *
 * <p>So this asserts the bean genuinely is absent under the test profile. If someone later drops
 * the {@code @ConditionalOnProperty}, or the property goes missing from the test yml, this fails
 * immediately and says why, instead of the suite becoming quietly flaky.
 */
public class SweepDisabledInTestsIntegrationTest extends BaseIntegrationTest {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void theScheduledSweepIsNotRegisteredUnderTheTestProfile() {
        assertEquals(0,
                applicationContext.getBeanNamesForType(CreativeChangeNotificationSweep.class).length,
                "envision.active-campaign.sweep.enabled must stay false in src/test/resources/application.yml "
                        + "— a live sweep pollutes the shared EmailService mock across unrelated test classes");
    }
}
