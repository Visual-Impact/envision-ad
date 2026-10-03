package com.envisionad.webservice.bundle.businesslogiclayer;

import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
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
    class BusinessTypeExclusion {

        @Mock
        private BusinessRepository businessRepository;

        @InjectMocks
        private BusinessTypeExclusionFilter filter;

        private Media inVenue(String venueId) {
            Media media = media(UUID.randomUUID(), Status.ACTIVE);
            media.setVenueId(venueId);
            return media;
        }

        private void givenBuyerOfType(String businessTypeVenueId) {
            Business buyer = new Business();
            buyer.setBusinessTypeVenueId(businessTypeVenueId);
            when(businessRepository.findByBusinessId_BusinessId("business-1")).thenReturn(buyer);
        }

        @Test
        void dropsMediaInTheBuyersOwnBusinessType() {
            Media rivalGym = inVenue("venue-gym");
            Media cafe = inVenue("venue-cafe");
            givenBuyerOfType("venue-gym");

            assertEquals(List.of(cafe), filter.filter(List.of(rivalGym, cafe), CONTEXT));
        }

        @Test
        void buyerWithNoBusinessTypeExcludesNothing() {
            List<Media> input = List.of(inVenue("venue-gym"), inVenue(null));
            givenBuyerOfType(null);

            assertEquals(input, filter.filter(input, CONTEXT));
        }

        @Test
        void blankBusinessTypeExcludesNothing() {
            List<Media> input = List.of(inVenue("venue-gym"));
            givenBuyerOfType("  ");

            assertEquals(input, filter.filter(input, CONTEXT));
        }

        @Test
        void mediaWithNoVenueIsNeverExcluded() {
            Media untagged = inVenue(null);
            givenBuyerOfType("venue-gym");

            assertEquals(List.of(untagged), filter.filter(List.of(untagged), CONTEXT));
        }

        @Test
        void unknownBuyerExcludesNothing() {
            List<Media> input = List.of(inVenue("venue-gym"));
            when(businessRepository.findByBusinessId_BusinessId("business-1")).thenReturn(null);

            assertEquals(input, filter.filter(input, CONTEXT));
        }

        @Test
        void anonymousBrowsingSkipsTheRepositoryEntirely() {
            List<Media> input = List.of(inVenue("venue-gym"));

            assertEquals(input, filter.filter(input, new PricingContext("bundle-1", null)));
            verifyNoInteractions(businessRepository);
        }

        @Test
        void emptyCandidateSetSkipsTheRepositoryEntirely() {
            assertTrue(filter.filter(List.of(), CONTEXT).isEmpty());

            verifyNoInteractions(businessRepository);
        }

        @Test
        void sharesBusinessType_nullOnEitherSideNeverMatches() {
            assertFalse(BusinessTypeExclusionFilter.sharesBusinessType(null, inVenue(null)));
            assertFalse(BusinessTypeExclusionFilter.sharesBusinessType(null, inVenue("venue-gym")));
            assertFalse(BusinessTypeExclusionFilter.sharesBusinessType("venue-gym", inVenue(null)));
            assertTrue(BusinessTypeExclusionFilter.sharesBusinessType("venue-gym", inVenue("venue-gym")));
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
