package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StripeCustomerRepository extends JpaRepository<StripeCustomer, Long> {

    Optional<StripeCustomer> findByBusinessId(String businessId);

    Optional<StripeCustomer> findByStripeCustomerId(String stripeCustomerId);
}
