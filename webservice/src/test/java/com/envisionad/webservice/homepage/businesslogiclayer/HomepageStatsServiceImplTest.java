package com.envisionad.webservice.homepage.businesslogiclayer;

import com.envisionad.webservice.homepage.dataaccesslayer.HomepageStatsRepository;
import com.envisionad.webservice.homepage.presentationlayer.models.HomepageStatsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomepageStatsServiceImplTest {

    @Mock
    private HomepageStatsRepository repo;

    @InjectMocks
    private HomepageStatsServiceImpl service;

    @Test
    void getStats_mapsAllFieldsCorrectly() {
        when(repo.countActiveScreens()).thenReturn(21L);
        when(repo.countDistinctCities()).thenReturn(6L);
        when(repo.countVenueTypes()).thenReturn(8L);
        when(repo.sumMonthlyBroadcasts()).thenReturn(411600L);

        HomepageStatsResponse result = service.getStats();

        assertThat(result).isNotNull();
        assertThat(result.activeScreens()).isEqualTo(21L);
        assertThat(result.citiesCovered()).isEqualTo(6L);
        assertThat(result.venueTypes()).isEqualTo(8L);
        assertThat(result.monthlyBroadcasts()).isEqualTo(411600L);

        verify(repo).countActiveScreens();
        verify(repo).countDistinctCities();
        verify(repo).countVenueTypes();
        verify(repo).sumMonthlyBroadcasts();
        verifyNoMoreInteractions(repo);
    }

    @Test
    void getStats_handlesZeros() {
        when(repo.countActiveScreens()).thenReturn(0L);
        when(repo.countDistinctCities()).thenReturn(0L);
        when(repo.countVenueTypes()).thenReturn(0L);
        when(repo.sumMonthlyBroadcasts()).thenReturn(0L);

        HomepageStatsResponse result = service.getStats();

        assertThat(result.activeScreens()).isZero();
        assertThat(result.citiesCovered()).isZero();
        assertThat(result.venueTypes()).isZero();
        assertThat(result.monthlyBroadcasts()).isZero();
    }
}
