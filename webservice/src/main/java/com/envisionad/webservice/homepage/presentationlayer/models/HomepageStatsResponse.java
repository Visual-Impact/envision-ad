package com.envisionad.webservice.homepage.presentationlayer.models;

public record HomepageStatsResponse(
        long activeScreens,
        long citiesCovered,
        long venueTypes,
        long monthlyBroadcasts
) {}
