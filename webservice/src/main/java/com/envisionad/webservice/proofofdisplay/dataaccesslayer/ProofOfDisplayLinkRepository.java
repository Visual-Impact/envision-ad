package com.envisionad.webservice.proofofdisplay.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProofOfDisplayLinkRepository extends JpaRepository<ProofOfDisplayLink, UUID> {

    Optional<ProofOfDisplayLink> findBySubscriptionId(String subscriptionId);

    /** Batch load for the dashboards, so a list of N subscriptions is one query, not N. */
    List<ProofOfDisplayLink> findBySubscriptionIdIn(Collection<String> subscriptionIds);
}
