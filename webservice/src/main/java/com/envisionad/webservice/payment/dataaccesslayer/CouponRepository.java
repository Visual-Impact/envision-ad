package com.envisionad.webservice.payment.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCouponId(String couponId);

    /** Case-insensitive on purpose — codes are stored uppercased but looked up either way. */
    Optional<Coupon> findByCodeIgnoreCase(String code);

    List<Coupon> findAllByStatusNot(CouponStatus status);
}
