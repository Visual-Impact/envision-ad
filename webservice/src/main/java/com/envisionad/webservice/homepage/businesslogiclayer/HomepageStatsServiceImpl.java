package com.envisionad.webservice.homepage.businesslogiclayer;

import com.envisionad.webservice.homepage.dataaccesslayer.HomepageStatsRepository;
import com.envisionad.webservice.homepage.presentationlayer.models.HomepageStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomepageStatsServiceImpl implements HomepageStatsService {

    private final HomepageStatsRepository repo;

    @Override
    public HomepageStatsResponse getStats() {
        return new HomepageStatsResponse(
                repo.countActiveScreens(),
                repo.countDistinctCities(),
                repo.countVenueTypes(),
                repo.sumMonthlyBroadcasts()
        );
    }
}
