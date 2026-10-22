package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.BundlePayoutService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The job is a delegator; the retry logic is covered by {@code BundlePayoutRetryIntegrationTest}. */
class BundlePayoutRetryJobUnitTest {

    @Test
    void whenTheJobRuns_thenItRetriesInsufficientFundsPayouts() {
        BundlePayoutService service = mock(BundlePayoutService.class);

        new BundlePayoutRetryJob(service).retryInsufficientFundsPayouts();

        verify(service).retryInsufficientFundsPayouts();
    }
}
