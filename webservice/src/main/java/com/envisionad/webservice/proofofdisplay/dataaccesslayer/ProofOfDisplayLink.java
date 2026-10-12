package com.envisionad.webservice.proofofdisplay.dataaccesslayer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The proof-of-display link for one bundle subscription (P12): a URL to a page the media owner
 * maintains themselves, typically on Canva.
 * <p>
 * One row per subscription, edited in place. There is no status and no history: a row means the
 * link is set, no row means it is not. {@code subscriptionId} is a plain scalar column rather
 * than an association, matching how the payment module references subscriptions (decision D5).
 */
@Entity
@Table(name = "proof_of_display_links")
@Data
@NoArgsConstructor
public class ProofOfDisplayLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subscription_id", nullable = false, unique = true, length = 36)
    private String subscriptionId;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    /** Auth0 {@code sub} of whoever saved it last. A light audit trail, not shown in the UI. */
    @Column(name = "updated_by_user_id")
    private String updatedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Set by the service on every save rather than by {@code @UpdateTimestamp}, which only fires
     * when a column actually changes. Re-saving the same URL still counts as "checked it's
     * current" (FR-1.1: every save bumps it).
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
