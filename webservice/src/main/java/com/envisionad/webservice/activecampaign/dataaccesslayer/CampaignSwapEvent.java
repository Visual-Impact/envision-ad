package com.envisionad.webservice.activecampaign.dataaccesslayer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit row for initial selection, swaps, and manual/automatic notification sends.
 * Scalar business/campaign identifiers match the bundle-subscription model and avoid
 * introducing entity cascades into an append-only event record.
 */
@Entity
@Table(name = "campaign_swap_events")
@Data
@NoArgsConstructor
public class CampaignSwapEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, length = 36)
    private String businessId;

    @Column(name = "from_campaign_id", length = 36)
    private String fromCampaignId;

    @Column(name = "to_campaign_id", nullable = false, length = 36)
    private String toCampaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private CampaignSwapEventType eventType;

    @Column(name = "triggered_by_user_id", length = 64)
    private String triggeredByUserId;

    @CreationTimestamp
    @Column(name = "triggered_at", nullable = false, updatable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "recipients_notified", nullable = false)
    private int recipientsNotified;

    @Column(name = "recipients_failed", nullable = false)
    private int recipientsFailed;
}
