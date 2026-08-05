package com.envisionad.webservice.payment.dataaccesslayer;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A Stripe Customer for an advertiser business, required for Stripe Billing
 * subscriptions. Distinct from {@link StripeAccount}, which holds Connect accounts
 * for media owners.
 */
@Entity
@Table(name = "stripe_customers")
@Data
@NoArgsConstructor
public class StripeCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", unique = true, nullable = false, length = 36)
    private String businessId;

    @Column(name = "stripe_customer_id", unique = true, nullable = false)
    private String stripeCustomerId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
