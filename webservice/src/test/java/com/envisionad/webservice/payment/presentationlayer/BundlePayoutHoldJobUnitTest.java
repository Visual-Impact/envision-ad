package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.BundlePayoutService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The job is a delegator; the hold logic is covered by {@code BundlePayoutHoldIntegrationTest}. */
class BundlePayoutHoldJobUnitTest {

    @Test
    void whenTheJobRuns_thenItReleasesOrExpiresHeldPayouts() {
        BundlePayoutService service = mock(BundlePayoutService.class);

        new BundlePayoutHoldJob(service).releaseOrExpireHeldPayouts();

        verify(service).releaseOrExpireHeldPayouts();
    }
}
