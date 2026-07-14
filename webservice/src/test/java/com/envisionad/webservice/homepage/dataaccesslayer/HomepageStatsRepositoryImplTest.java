package com.envisionad.webservice.homepage.dataaccesslayer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HomepageStatsRepositoryImplTest {

    @Test
    void countActiveScreens_returnsLongValue() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(21);

        HomepageStatsRepositoryImpl repo = new HomepageStatsRepositoryImpl();
        inject(repo, em);

        assertEquals(21L, repo.countActiveScreens());
        verify(em).createNativeQuery(anyString());
        verify(q).getSingleResult();
    }

    @Test
    void countDistinctCities_returnsLongValue() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(6);

        HomepageStatsRepositoryImpl repo = new HomepageStatsRepositoryImpl();
        inject(repo, em);

        assertEquals(6L, repo.countDistinctCities());
    }

    @Test
    void countVenueTypes_returnsLongValue() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(8);

        HomepageStatsRepositoryImpl repo = new HomepageStatsRepositoryImpl();
        inject(repo, em);

        assertEquals(8L, repo.countVenueTypes());
    }

    @Test
    void sumMonthlyBroadcasts_returnsLongValue() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(400000L);

        HomepageStatsRepositoryImpl repo = new HomepageStatsRepositoryImpl();
        inject(repo, em);

        assertEquals(400000L, repo.sumMonthlyBroadcasts());
    }

    private static void inject(HomepageStatsRepositoryImpl repo, EntityManager em) throws Exception {
        Field f = HomepageStatsRepositoryImpl.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(repo, em);
    }
}
