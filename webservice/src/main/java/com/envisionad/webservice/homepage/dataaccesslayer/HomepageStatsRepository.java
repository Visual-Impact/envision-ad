package com.envisionad.webservice.homepage.dataaccesslayer;

public interface HomepageStatsRepository {
    long countActiveScreens();
    long countDistinctCities();
    long countVenueTypes();
    long sumMonthlyBroadcasts();
}
