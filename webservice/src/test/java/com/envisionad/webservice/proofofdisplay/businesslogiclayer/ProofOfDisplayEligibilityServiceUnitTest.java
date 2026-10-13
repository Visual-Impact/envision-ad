package com.envisionad.webservice.proofofdisplay.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProofOfDisplayEligibilityServiceUnitTest {

    @Mock private BundleSubscriptionItemRepository itemRepository;

    private ProofOfDisplayEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new ProofOfDisplayEligibilityService(itemRepository);
    }

    @Test
    void isSoleOwner_trueWhenTheBusinessOwnsEveryScreen() {
        when(itemRepository.findDistinctMediaOwnerBusinessIdsBySubscriptionId("sub")).thenReturn(List.of("owner-a"));
        assertTrue(service.isSoleOwner("sub", "owner-a"));
    }

    @Test
    void isSoleOwner_falseForAnotherBusiness() {
        when(itemRepository.findDistinctMediaOwnerBusinessIdsBySubscriptionId("sub")).thenReturn(List.of("owner-a"));
        assertFalse(service.isSoleOwner("sub", "owner-b"));
    }

    @Test
    void isSoleOwner_falseForAPartOwnerOfAMultiOwnerSubscription() {
        when(itemRepository.findDistinctMediaOwnerBusinessIdsBySubscriptionId("sub"))
                .thenReturn(List.of("owner-a", "owner-b"));
        assertFalse(service.isSoleOwner("sub", "owner-a"));
    }

    @Test
    void isSingleOwner_handlesEmptyAndNull() {
        assertFalse(ProofOfDisplayEligibilityService.isSingleOwner(Set.of()));
        assertFalse(ProofOfDisplayEligibilityService.isSingleOwner(null));
        assertTrue(ProofOfDisplayEligibilityService.isSingleOwner(Set.of("owner-a")));
    }
}
