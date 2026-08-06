package com.envisionad.webservice.payment.dataaccesslayer;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One media owner's payout for one paid invoice.
 *
 * <p>Serves two purposes at once: the idempotency guard against Stripe webhook
 * redelivery (a row for {@code (stripeInvoiceId, mediaOwnerBusinessId)} means
 * "already handled, do nothing"), and the payout audit trail.
 *
 * <p>Redelivery is not an edge case here. {@code invoice.paid} deliberately throws
 * on a lookup miss so Stripe retries, so the same event legitimately arrives more
 * than once; and Stripe's own idempotency keys expire well before its ~3-day retry
 * schedule ends. This table is the durable record.
 */
@Entity
@Table(name = "bundle_payouts")
@Data
@NoArgsConstructor
public class BundlePayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The payout unit is the invoice: each monthly renewal is its own invoice. */
    @Column(name = "stripe_invoice_id", nullable = false)
    private String stripeInvoiceId;

    /** Local {@code bundle_subscriptions.subscription_id}; no FK, so the record outlives a bundle delete. */
    @Column(name = "subscription_id", nullable = false, length = 36)
    private String subscriptionId;

    @Column(name = "media_owner_business_id", nullable = false, length = 36)
    private String mediaOwnerBusinessId;

    /**
     * The owner's share before the platform fee: the sum of that owner's
     * {@link BundleSubscriptionItem#getMonthlyAmount()} values, which hold each
     * screen's full undiscounted price. Never scaled by the bundle discount.
     */
    @Column(name = "gross_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossAmount;

    /** What was actually transferred: gross minus {@code stripe.platform-fee-percent}. */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Null unless {@link #status} is {@link BundlePayoutStatus#PAID}. */
    @Column(name = "stripe_transfer_id")
    private String stripeTransferId;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private BundlePayoutStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
