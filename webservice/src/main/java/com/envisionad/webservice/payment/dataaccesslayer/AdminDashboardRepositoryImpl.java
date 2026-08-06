package com.envisionad.webservice.payment.dataaccesslayer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class AdminDashboardRepositoryImpl implements AdminDashboardRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public long countOrganizations() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM business").getSingleResult()).longValue();
    }

    @Override
    public long countMediaListings() {
        return ((Number) em.createNativeQuery("""
            SELECT COUNT(*)
            FROM media
            WHERE status = 'ACTIVE'
            """).getSingleResult()).longValue();
    }


    /**
     * ⚠️ Changed meaning in P1 M6, deliberately (decision D43). This used to be the cumulative
     * lifetime value of every CONFIRMED reservation. Under a subscription model the equivalent
     * figure is <strong>monthly recurring revenue</strong> — the sum of what live subscriptions
     * bill each month — which is a run-rate, not a running total. The admin tile is labelled
     * accordingly; do not read this as "revenue to date".
     */
    @Override
    public BigDecimal sumPlatformRevenue() {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(monthly_amount), 0)
                FROM bundle_subscriptions
                WHERE status IN ('ACTIVE', 'PAST_DUE')
                """).getSingleResult();
        return (result instanceof BigDecimal bd) ? bd : new BigDecimal(result.toString());
    }

    /**
     * The {@code reservations.advertiser_id} arm of this UNION was dropped in M6 with the table.
     * It is not replaced by {@code bundle_subscriptions.advertiser_business_id}: that column holds
     * a <em>business</em> id, whereas every other arm here holds a <em>user</em> id, so unioning
     * them would inflate the count with values from a different id space. Advertisers who are
     * employees of a business are still counted through the {@code employee} arm.
     */
    @Override
    public long countDistinctKnownUsers() {
        return ((Number) em.createNativeQuery("""
                SELECT COUNT(DISTINCT user_id) FROM (
                    SELECT owner_id AS user_id FROM business
                    UNION
                    SELECT user_id AS user_id FROM employee
                ) u
                """).getSingleResult()).longValue();
    }

    @Override
    public long countMediaOwners() {
        return ((Number) em.createNativeQuery("""
                SELECT COUNT(DISTINCT owner_id)
                FROM business
                WHERE media_owner = true
                """).getSingleResult()).longValue();
    }

    /**
     * "Advertisers" now means businesses that have ever subscribed to a bundle, in any state —
     * matching the old query, which counted every distinct advertiser on any reservation
     * regardless of its status, rather than only currently-active ones.
     */
    @Override
    public long countAdvertisers() {
        return ((Number) em.createNativeQuery("""
                SELECT COUNT(DISTINCT advertiser_business_id)
                FROM bundle_subscriptions
                """).getSingleResult()).longValue();
    }
}
