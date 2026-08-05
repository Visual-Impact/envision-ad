package com.envisionad.webservice.bundle.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface BundleExcludedMediaRepository
        extends JpaRepository<BundleExcludedMedia, BundleExcludedMediaId> {

    List<BundleExcludedMedia> findAllByIdBundleId(String bundleId);

    boolean existsByIdBundleIdAndIdMediaId(String bundleId, UUID mediaId);

    // Derived delete queries, unlike the inherited CrudRepository#delete, are not
    // transactional by default and fail outside an active transaction.
    @Transactional
    void deleteByIdBundleIdAndIdMediaId(String bundleId, UUID mediaId);
}
