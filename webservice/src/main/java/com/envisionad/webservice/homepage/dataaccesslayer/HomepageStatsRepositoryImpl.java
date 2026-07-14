package com.envisionad.webservice.homepage.dataaccesslayer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class HomepageStatsRepositoryImpl implements HomepageStatsRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public long countActiveScreens() {
        return ((Number) em.createNativeQuery("""
                SELECT COUNT(*)
                FROM media
                WHERE status = 'ACTIVE'
                """).getSingleResult()).longValue();
    }

    @Override
    public long countDistinctCities() {
        return ((Number) em.createNativeQuery("""
                SELECT COUNT(DISTINCT ml.city)
                FROM media m
                JOIN media_location ml ON m.media_location_id = ml.media_location_id
                WHERE m.status = 'ACTIVE'
                """).getSingleResult()).longValue();
    }

    @Override
    public long countVenueTypes() {
        return ((Number) em.createNativeQuery("""
                SELECT COUNT(*)
                FROM venue
                """).getSingleResult()).longValue();
    }

    @Override
    public long sumMonthlyBroadcasts() {
        return ((Number) em.createNativeQuery("""
                SELECT COALESCE(SUM(CAST(daily_impressions AS BIGINT) * CAST(active_days AS BIGINT)), 0) * 4
                FROM media
                WHERE status = 'ACTIVE'
                """).getSingleResult()).longValue();
    }
}
