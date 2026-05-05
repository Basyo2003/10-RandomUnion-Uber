package com.team10.uber.payment.repository;

import com.team10.uber.payment.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    List<Coupon> findByActiveTrue();

    @Query(value = "SELECT c.id, c.code, c.discount_type, c.discount_value, " +
            "COUNT(pc.id) as times_used, COALESCE(SUM(pc.discount_applied), 0) as total_discount, " +
            "c.active, c.expiry_date " +
            "FROM coupons c " +
            "LEFT JOIN payment_coupons pc ON c.id = pc.coupon_id " +
            "GROUP BY c.id, c.code, c.discount_type, c.discount_value, " +
            "c.active, c.expiry_date " +
            "ORDER BY times_used DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findTopUsedCoupons(@Param("limit") int limit);
}
