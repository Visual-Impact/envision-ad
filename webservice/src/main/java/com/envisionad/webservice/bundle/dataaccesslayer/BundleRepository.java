package com.envisionad.webservice.bundle.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BundleRepository extends JpaRepository<Bundle, Long> {

    Optional<Bundle> findByBundleId(String bundleId);

    boolean existsByBundleId(String bundleId);

    List<Bundle> findAllByActiveTrue();

    List<Bundle> findAllByRuleType(BundleRuleType ruleType);
}
