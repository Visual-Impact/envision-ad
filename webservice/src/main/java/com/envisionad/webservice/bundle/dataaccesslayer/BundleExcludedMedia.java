package com.envisionad.webservice.bundle.dataaccesslayer;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * An admin-managed manual exclusion: this media matches the bundle's rule, but the
 * admin removed it from that specific bundle.
 */
@Entity
@Table(name = "bundle_excluded_medias")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleExcludedMedia {

    @EmbeddedId
    private BundleExcludedMediaId id;

    public BundleExcludedMedia(String bundleId, UUID mediaId) {
        this.id = new BundleExcludedMediaId(bundleId, mediaId);
    }
}
