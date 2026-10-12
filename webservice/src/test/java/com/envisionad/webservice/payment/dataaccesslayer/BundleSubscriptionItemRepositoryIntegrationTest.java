package com.envisionad.webservice.payment.dataaccesslayer;

import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus.ACTIVE;
import static com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus.CANCELED;
import static com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus.INCOMPLETE;
import static com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus.PAST_DUE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The queries P12 (proof of display via link) reads subscriptions through. They group by the
 * denormalized {@code media_owner_business_id} on the frozen items, so they need a real Postgres
 * rather than a mock. The pre-P12 proof-of-display readers this file used to cover were deleted
 * with the photo-email flow.
 */
class BundleSubscriptionItemRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final String OWNER_A = "owner-biz-a";
    private static final String OWNER_B = "owner-biz-b";

    @Autowired private BundleSubscriptionRepository subscriptionRepository;
    @Autowired private BundleSubscriptionItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    /** A subscription with one item per listed owner (repeats allowed). Returns its id. */
    private String givenSubscription(BundleSubscriptionStatus status, String... owners) {
        BundleSubscription subscription = new BundleSubscription();
        String subscriptionId = UUID.randomUUID().toString();
        subscription.setSubscriptionId(subscriptionId);
        subscription.setBundleId(UUID.randomUUID().toString());
        subscription.setAdvertiserBusinessId("advertiser-biz-1");
        subscription.setStripeCheckoutSessionId("cs_" + UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setMonthlyAmount(new BigDecimal("10.00"));
        subscription.setScreenCount(owners.length);
        subscriptionRepository.save(subscription);

        for (String owner : owners) {
            BundleSubscriptionItem item = new BundleSubscriptionItem();
            item.setSubscriptionId(subscriptionId);
            item.setMediaId(UUID.randomUUID());
            item.setMediaOwnerBusinessId(owner);
            item.setMonthlyAmount(new BigDecimal("5.00"));
            itemRepository.save(item);
        }
        return subscriptionId;
    }

    @Test
    void distinctOwners_collapsesSeveralScreensOfOneOwnerToOne() {
        String subscriptionId = givenSubscription(ACTIVE, OWNER_A, OWNER_A, OWNER_A);

        assertEquals(List.of(OWNER_A),
                itemRepository.findDistinctMediaOwnerBusinessIdsBySubscriptionId(subscriptionId));
    }

    @Test
    void distinctOwners_listsEveryOwnerOfAMultiOwnerSubscription() {
        String subscriptionId = givenSubscription(ACTIVE, OWNER_A, OWNER_B, OWNER_A);

        assertEquals(Set.of(OWNER_A, OWNER_B), Set.copyOf(
                itemRepository.findDistinctMediaOwnerBusinessIdsBySubscriptionId(subscriptionId)));
    }

    @Test
    void findAllBySubscriptionIdIn_returnsOnlyTheRequestedSubscriptionsItems() {
        String first = givenSubscription(ACTIVE, OWNER_A, OWNER_B);
        String second = givenSubscription(ACTIVE, OWNER_A);
        givenSubscription(ACTIVE, OWNER_B);

        List<BundleSubscriptionItem> items = itemRepository.findAllBySubscriptionIdIn(List.of(first, second));

        assertEquals(3, items.size());
        assertTrue(items.stream().allMatch(i -> Set.of(first, second).contains(i.getSubscriptionId())));
    }

    @Test
    void coveringMediaOwner_findsEachSubscriptionOnce_inTheRequestedStatusesOnly() {
        String twoOfMyScreens = givenSubscription(ACTIVE, OWNER_A, OWNER_A);
        String shared = givenSubscription(PAST_DUE, OWNER_B, OWNER_A);
        String canceled = givenSubscription(CANCELED, OWNER_A);
        givenSubscription(INCOMPLETE, OWNER_A);
        givenSubscription(ACTIVE, OWNER_B);

        List<BundleSubscription> found = subscriptionRepository.findAllCoveringMediaOwnerBusiness(
                OWNER_A, List.of(ACTIVE, PAST_DUE, CANCELED));

        assertEquals(Set.of(twoOfMyScreens, shared, canceled),
                Set.copyOf(found.stream().map(BundleSubscription::getSubscriptionId).toList()));
        assertEquals(3, found.size(), "DISTINCT: two of my screens on one subscription is still one row");
    }
}
