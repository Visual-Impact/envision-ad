package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BundleSubscriptionRepository extends JpaRepository<BundleSubscription, Long> {

    Optional<BundleSubscription> findBySubscriptionId(String subscriptionId);

    Optional<BundleSubscription> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    Optional<BundleSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<BundleSubscription> findAllByAdvertiserBusinessId(String advertiserBusinessId);

    List<BundleSubscription> findAllByBundleIdAndStatusIn(
            String bundleId, Collection<BundleSubscriptionStatus> statuses);

    long countByBundleIdAndStatusIn(
            String bundleId, Collection<BundleSubscriptionStatus> statuses);
}
