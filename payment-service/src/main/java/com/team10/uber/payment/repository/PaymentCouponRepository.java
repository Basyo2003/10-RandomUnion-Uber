package com.team10.uber.payment.repository;

import com.team10.uber.payment.model.PaymentCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentCouponRepository extends JpaRepository<PaymentCoupon, Long> {

    List<PaymentCoupon> findByPaymentId(Long paymentId);

    Optional<PaymentCoupon> findByPaymentIdAndCouponId(Long paymentId, Long couponId);

    @Query(value = "SELECT * FROM payment_coupons WHERE payment_id = :paymentId",
            nativeQuery = true)
    List<PaymentCoupon> findAllByPaymentId(@Param("paymentId") Long paymentId);
}