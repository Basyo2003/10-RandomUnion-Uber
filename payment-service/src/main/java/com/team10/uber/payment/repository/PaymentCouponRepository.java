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

    @Query(value = "SELECT c.id, c.code, c.discount_type, c.discount_value, " +
               "c.current_uses, COALESCE(SUM(pc.discount_applied), 0) AS total_discount, " +
               "c.active, c.expiry_date " +
               "FROM coupons c " +
               "JOIN payment_coupons pc ON pc.coupon_id = c.id " +
               "GROUP BY c.id, c.code, c.discount_type, c.discount_value, " +
               "c.current_uses, c.active, c.expiry_date " +
               "ORDER BY c.current_uses DESC " +
               "LIMIT :limit",
       nativeQuery = true)
    List<Object[]> findTopUsedCoupons(@Param("limit") int limit);
}