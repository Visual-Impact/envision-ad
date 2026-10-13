package com.envisionad.webservice.proofofdisplay.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItem;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Who may maintain a subscription's proof link (P12). In v1 only a subscription whose screens all
 * belong to one media-owner business can carry a link.
 * <p>
 * Always computed from {@code bundle_subscription_items}, never cached: the items are frozen at
 * checkout, so whether a subscription is single-owner never changes for its whole life.
 */
@Service
public class ProofOfDisplayEligibilityService {

    private final BundleSubscriptionItemRepository itemRepository;

    public ProofOfDisplayEligibilityService(BundleSubscriptionItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /** The media-owner businesses whose screens this subscription covers. */
    public Set<String> findOwnerBusinessIds(String subscriptionId) {
        return new LinkedHashSet<>(itemRepository.findDistinctMediaOwnerBusinessIdsBySubscriptionId(subscriptionId));
    }

    /** True when {@code businessId} owns every screen the subscription covers. */
    public boolean isSoleOwner(String subscriptionId, String businessId) {
        Set<String> owners = findOwnerBusinessIds(subscriptionId);
        return owners.size() == 1 && owners.contains(businessId);
    }

    /**
     * The same answer for many subscriptions at once, grouped from items the caller has already
     * loaded, so a dashboard of N subscriptions costs no extra queries.
     */
    public Map<String, Set<String>> ownerBusinessIdsBySubscription(Collection<BundleSubscriptionItem> items) {
        Map<String, Set<String>> owners = new HashMap<>();
        for (BundleSubscriptionItem item : items) {
            owners.computeIfAbsent(item.getSubscriptionId(), id -> new HashSet<>())
                    .add(item.getMediaOwnerBusinessId());
        }
        return owners;
    }

    /** One owner, exactly. A subscription with no items (should not exist) is not eligible. */
    public static boolean isSingleOwner(Set<String> owners) {
        return owners != null && owners.size() == 1;
    }
}
