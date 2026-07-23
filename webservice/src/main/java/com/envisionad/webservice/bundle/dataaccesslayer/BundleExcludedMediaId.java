package com.envisionad.webservice.bundle.dataaccesslayer;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite key for {@link BundleExcludedMedia} — the (bundle, media) pair IS the
 * identity, so excluding the same media twice is structurally impossible.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleExcludedMediaId implements Serializable {

    @Column(name = "bundle_id", nullable = false, length = 36)
    private String bundleId;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;
}
