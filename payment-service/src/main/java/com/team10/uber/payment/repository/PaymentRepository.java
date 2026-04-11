package com.team10.uber.payment.repository;

import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserId(Long userId);

    List<Payment> findByRideId(Long rideId);

    Optional<Payment> findByRideIdAndStatus(Long rideId, PaymentStatus status);

    @Query(value = "SELECT * FROM payments WHERE " +
            "created_at >= :startDate AND created_at <= :endDate " +
            "ORDER BY created_at DESC",
            nativeQuery = true)
    List<Payment> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT * FROM payments WHERE " +
            "CAST(status AS TEXT) = :status AND " +
            "created_at >= :startDate AND created_at <= :endDate " +
            "ORDER BY created_at DESC",
            nativeQuery = true)
    List<Payment> findByStatusAndDateRange(
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT * FROM payments WHERE " +
            "user_id = :userId AND status = 'COMPLETED'",
            nativeQuery = true)
    List<Payment> findCompletedPaymentsByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT method, COUNT(*) as count, SUM(amount) as total " +
            "FROM payments WHERE user_id = :userId AND status = 'COMPLETED' " +
            "GROUP BY method",
            nativeQuery = true)
    List<Object[]> findPaymentSummaryByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT SUM(amount), COUNT(*), AVG(amount) FROM payments " +
            "WHERE status = 'COMPLETED' AND created_at BETWEEN :startDate AND :endDate",
            nativeQuery = true)
    Object[] findRevenueStats(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT SUM(amount), COUNT(*) FROM payments " +
            "WHERE status = 'REFUNDED' AND created_at BETWEEN :startDate AND :endDate",
            nativeQuery = true)
    Object[] findRefundStats(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Check ride exists and get its status
    @Query(value = "SELECT status FROM rides WHERE id = :rideId", nativeQuery = true)
    String findRideStatusById(@Param("rideId") Long rideId);

    // Get ride fare
    @Query(value = "SELECT fare FROM rides WHERE id = :rideId", nativeQuery = true)
    Double findRideFareById(@Param("rideId") Long rideId);

    // Get ride userId
    @Query(value = "SELECT user_id FROM rides WHERE id = :rideId", nativeQuery = true)
    Long findRideUserIdById(@Param("rideId") Long rideId);

    @Query(value = "SELECT COUNT(*) FROM users WHERE id = :userId", nativeQuery = true)
    int countUserById(@Param("userId") Long userId);

    // Check ride exists
    @Query(value = "SELECT COUNT(*) FROM rides WHERE id = :rideId", nativeQuery = true)
    int countRideById(@Param("rideId") Long rideId);

    // Check ride already has a payment
    @Query(value = "SELECT COUNT(*) FROM payments WHERE ride_id = :rideId", nativeQuery = true)
    int countPaymentsByRideId(@Param("rideId") Long rideId);
}