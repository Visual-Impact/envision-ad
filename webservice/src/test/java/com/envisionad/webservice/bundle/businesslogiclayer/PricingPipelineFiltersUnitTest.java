package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.BundleExcludedMedia;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleExcludedMediaRepository;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The individual links of the eligibility chain. Their composition and ordering is
 * covered by {@link BundlePricingServiceUnitTest}.
 */
@ExtendWith(MockitoExtension.class)
class PricingPipelineFiltersUnitTest {

    private static final PricingContext CONTEXT = new PricingContext("bundle-1", "business-1");

    private static Media media(UUID id, Status status) {
        Media media = new Media();
        media.setId(id);
        media.setStatus(status);
        media.setPrice(new BigDecimal("4.00"));
        return media;
    }

    @Nested
    class ActiveStatusExclusion {

        private final ActiveStatusExclusionFilter filter = new ActiveStatusExclusionFilter();

        @Test
        void keepsOnlyActiveMedia() {
            Media active = media(UUID.randomUUID(), Status.ACTIVE);
            Media inactive = media(UUID.randomUUID(), Status.INACTIVE);
            Media pending = media(UUID.randomUUID(), Status.PENDING);
            Media rejected = media(UUID.randomUUID(), Status.REJECTED);

            List<Media> result = filter.filter(List.of(active, inactive, pending, rejected), CONTEXT);

            assertEquals(List.of(active), result);
        }

        @Test
        void emptyInputYieldsEmptyOutput() {
            assertTrue(filter.filter(List.of(), CONTEXT).isEmpty());
        }
    }

    @Nested
    class ManualExclusion {

        @Mock
        private BundleExcludedMediaRepository excludedMediaRepository;

        @InjectMocks
        private ManualExclusionFilter filter;

        @Test
        void dropsExactlyTheExcludedIds() {
            UUID keptId = UUID.randomUUID();
            UUID excludedId = UUID.randomUUID();
            Media kept = media(keptId, Status.ACTIVE);
            Media excluded = media(excludedId, Status.ACTIVE);

            when(excludedMediaRepository.findAllByIdBundleId("bundle-1"))
                    .thenReturn(List.of(new BundleExcludedMedia("bundle-1", excludedId)));

            List<Media> result = filter.filter(List.of(kept, excluded), CONTEXT);

            assertEquals(List.of(kept), result);
        }

        @Test
        void withNoExclusionsReturnsInputUntouched() {
            Media first = media(UUID.randomUUID(), Status.ACTIVE);
            Media second = media(UUID.randomUUID(), Status.ACTIVE);
            when(excludedMediaRepository.findAllByIdBundleId("bundle-1")).thenReturn(List.of());

            List<Media> input = List.of(first, second);

            assertEquals(input, filter.filter(input, CONTEXT));
        }

        @Test
        void exclusionsAreScopedToTheContextBundle() {
            Media onlyMedia = media(UUID.randomUUID(), Status.ACTIVE);
            when(excludedMediaRepository.findAllByIdBundleId("bundle-1")).thenReturn(List.of());

            filter.filter(List.of(onlyMedia), CONTEXT);

            verify(excludedMediaRepository).findAllByIdBundleId("bundle-1");
            verifyNoMoreInteractions(excludedMediaRepository);
        }

        @Test
        void emptyCandidateSetSkipsTheRepositoryEntirely() {
            assertTrue(filter.filter(List.of(), CONTEXT).isEmpty());

            verifyNoInteractions(excludedMediaRepository);
        }
    }

    @Nested
    class NoOpDiscount {

        private final NoOpDiscountModifier modifier = new NoOpDiscountModifier();

        @Test
        void returnsTheBaseAmountUnchanged() {
            BigDecimal base = new BigDecimal("123.45");

            assertSame(base, modifier.apply(base, CONTEXT));
        }

        @Test
        void handlesZero() {
            assertEquals(0, BigDecimal.ZERO.compareTo(modifier.apply(BigDecimal.ZERO, CONTEXT)));
        }
    }
}
