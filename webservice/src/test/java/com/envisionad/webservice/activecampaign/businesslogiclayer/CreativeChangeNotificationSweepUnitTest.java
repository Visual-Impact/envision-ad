package com.envisionad.webservice.activecampaign.businesslogiclayer;

import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The sweep's own job: pick the right window, and never let one business sink the rest. */
@ExtendWith(MockitoExtension.class)
class CreativeChangeNotificationSweepUnitTest {

    private static final Duration QUIET_PERIOD = Duration.ofMinutes(60);

    @Mock private BusinessRepository businessRepository;
    @Mock private ActiveCampaignService activeCampaignService;

    private CreativeChangeNotificationSweep sweep;

    @BeforeEach
    void setUp() {
        sweep = new CreativeChangeNotificationSweep(businessRepository, activeCampaignService, QUIET_PERIOD);
    }

    @Test
    void itAsksForChangesOlderThanTheQuietPeriod_andOnlyForLiveSubscribers() {
        when(businessRepository.findBusinessesWithCreativeChangesOlderThan(any(), any()))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now();
        sweep.notifyOwnersOfUnannouncedCreativeChanges();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(businessRepository).findBusinessesWithCreativeChangesOlderThan(
                cutoff.capture(), eq(BundleSubscriptionStatus.LIVE));
        // Bounded on both sides by when the call actually happened: the cutoff is "a quiet period
        // ago" as measured inside the sweep, which is somewhere within this window.
        assertFalse(cutoff.getValue().isBefore(before.minus(QUIET_PERIOD)),
                "the cutoff must not reach further back than one quiet period");
        assertFalse(cutoff.getValue().isAfter(after.minus(QUIET_PERIOD)),
                "the cutoff must be a full quiet period in the past, not less");
    }

    @Test
    void withNoCandidates_itDoesNothingAtAll() {
        when(businessRepository.findBusinessesWithCreativeChangesOlderThan(any(), any()))
                .thenReturn(List.of());

        sweep.notifyOwnersOfUnannouncedCreativeChanges();

        verifyNoInteractions(activeCampaignService);
    }

    /**
     * One business's failure — a campaign deleted mid-sweep, an unreachable Auth0 — must not
     * strand every business queued behind it until the next run an interval later.
     */
    @Test
    void oneBusinessFailing_doesNotStopTheRest() {
        when(businessRepository.findBusinessesWithCreativeChangesOlderThan(any(), any()))
                .thenReturn(List.of(business("biz-1"), business("biz-2"), business("biz-3")));
        when(activeCampaignService.autoNotifyIfCreativeChangesArePending("biz-2"))
                .thenThrow(new RuntimeException("Auth0 unreachable"));

        assertDoesNotThrow(sweep::notifyOwnersOfUnannouncedCreativeChanges);

        verify(activeCampaignService).autoNotifyIfCreativeChangesArePending("biz-1");
        verify(activeCampaignService).autoNotifyIfCreativeChangesArePending("biz-3");
    }

    @Test
    void everyCandidateIsOfferedExactlyOnce() {
        when(businessRepository.findBusinessesWithCreativeChangesOlderThan(any(), any()))
                .thenReturn(List.of(business("biz-1"), business("biz-2")));

        sweep.notifyOwnersOfUnannouncedCreativeChanges();

        verify(activeCampaignService, times(1)).autoNotifyIfCreativeChangesArePending("biz-1");
        verify(activeCampaignService, times(1)).autoNotifyIfCreativeChangesArePending("biz-2");
        verify(activeCampaignService, never()).autoNotifyIfCreativeChangesArePending(
                argThat(id -> !List.of("biz-1", "biz-2").contains(id)));
    }

    /**
     * FR-8.5, and the rule most likely to be "tidied" away later: the sweep does not consult the
     * swap/notify cooldown. That cooldown stops a person hammering a button; this is already
     * rate-limited far more strictly by its own quiet period, and letting a recent manual action
     * suppress it would reopen the gap FR-8 exists to close.
     */
    @Test
    void itNeverConsultsTheSwapCooldown() {
        when(businessRepository.findBusinessesWithCreativeChangesOlderThan(any(), any()))
                .thenReturn(List.of(business("biz-1")));

        sweep.notifyOwnersOfUnannouncedCreativeChanges();

        verify(activeCampaignService).autoNotifyIfCreativeChangesArePending("biz-1");
        // The debounce lives behind swap/notify; the sweep must reach neither of them.
        verify(activeCampaignService, never()).swapActiveCampaign(any(), anyString(), anyString());
        verify(activeCampaignService, never()).notifyMediaOwners(any(), anyString());
    }

    private Business business(String businessId) {
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(businessId));
        return business;
    }
}
