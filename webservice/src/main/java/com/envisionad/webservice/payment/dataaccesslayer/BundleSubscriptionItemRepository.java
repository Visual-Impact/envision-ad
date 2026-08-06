package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BundleSubscriptionItemRepository extends JpaRepository<BundleSubscriptionItem, Long> {

    List<BundleSubscriptionItem> findAllBySubscriptionId(String subscriptionId);

    /** Clears a retried checkout's stale split before the fresh one is written. */
    void deleteAllBySubscriptionId(String subscriptionId);
}
