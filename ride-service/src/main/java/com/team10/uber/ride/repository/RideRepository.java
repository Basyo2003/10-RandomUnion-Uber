package com.team10.uber.ride.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.enums.RideStatus;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {

        long countByDriverIdAndStatusIn(Long driverId, Collection<RideStatus> statuses);

        @Query(value = """
                        SELECT COUNT(*)::bigint, COALESCE(SUM(fare), 0)
                        FROM rides
                        WHERE driver_id = :driverId
                          AND status = 'COMPLETED'
                          AND (CAST(:start AS timestamp) IS NULL OR requested_at >= :start)
                          AND (CAST(:end AS timestamp) IS NULL OR requested_at < :end)
                        """, nativeQuery = true)
        Object[] summarizeDriverCompletedRides(
                        @Param("driverId") Long driverId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query(value = "SELECT COUNT(*) FROM drivers WHERE id = :driverId", nativeQuery = true)
        int countDriversById(@Param("driverId") Long driverId);

        @Query(value = "SELECT COUNT(*) FROM drivers WHERE id = :driverId AND status = 'BUSY'", nativeQuery = true)
        int countBusyDriversById(@Param("driverId") Long driverId);

        @Modifying
        @Query(value = "UPDATE drivers SET status = 'AVAILABLE' WHERE id = :driverId AND status = 'BUSY'", nativeQuery = true)
        int updateDriverStatusToAvailable(@Param("driverId") Long driverId);

        // 2) Insert payment record
        @Modifying
        @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
        @Query(value = """
                        INSERT INTO payments (ride_id, user_id, amount, method, status, transaction_details, created_at)
                        VALUES (:rideId, :userId, :amount, 'CASH', 'PENDING', '{}'::jsonb, CURRENT_TIMESTAMP)
                        """, nativeQuery = true)
        void createPayment(
                        @Param("rideId") Long rideId,
                        @Param("userId") Long userId,
                        @Param("amount") Double amount);

        @Query(value = "SELECT * FROM rides WHERE metadata ->> :key = :value", nativeQuery = true)
        List<Ride> findByMetadataField(@Param("key") String key, @Param("value") String value);

        @Query(value = "SELECT COUNT(*) FROM rides WHERE pickup_latitude BETWEEN :minLat AND :maxLat AND pickup_longitude BETWEEN :minLon AND :maxLon AND status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS')", nativeQuery = true)
        int countActiveRidesNearPickup(@Param("minLat") Double minLat, @Param("maxLat") Double maxLat,
                        @Param("minLon") Double minLon, @Param("maxLon") Double maxLon);

        List<Ride> findByRequestedAtBetween(LocalDateTime startDateTime, LocalDateTime endDateTime);

        @Query(value = "SELECT * FROM rides " +
                        "WHERE requested_at BETWEEN :startDate AND :endDate " +
                        "AND (CAST(:status AS text) IS NULL OR status::text = :status) " +
                        "ORDER BY requested_at DESC", nativeQuery = true)
        List<Ride> getRidesByStatusAndDateRange(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("status") String status);

        @Query(value = "SELECT * FROM rides " +
                        "WHERE (CAST(:startDate AS timestamp) IS NULL OR requested_at >= :startDate) " +
                        "AND (CAST(:endDate AS timestamp) IS NULL OR requested_at <= :endDate) " +
                        "AND (CAST(:status AS text) IS NULL OR status::text = :status) " +
                        "AND (CAST(:userId AS bigint) IS NULL OR user_id = :userId) " +
                        "ORDER BY requested_at DESC", nativeQuery = true)
        List<Ride> searchRides(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("status") String status,
                        @Param("userId") Long userId);

        @Query(value = "SELECT COUNT(*) > 0 FROM drivers WHERE id = :driverId", nativeQuery = true)
        boolean driverexists(@Param("driverId") Long driverId);

        @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE id = :userId", nativeQuery = true)
        boolean userExistsById(@Param("userId") Long userId);

        @Query(value = "SELECT status FROM drivers WHERE id = :driverId", nativeQuery = true)
        String findStatusByDriverId(@Param("driverId") Long driverId);

        @Modifying
        @Query(value = "UPDATE drivers SET status = 'BUSY' WHERE id = :driverId AND status = 'AVAILABLE'", nativeQuery = true)
        int updateDriverStatusToBusy(@Param("driverId") Long driverId);

        @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE id = :userId", nativeQuery = true)
        boolean userExists(@Param("userId") Long userId);

        @Query(value = """
                        SELECT COALESCE(SUM(fare), 0)
                        FROM rides
                        WHERE status = 'COMPLETED'
                          AND requested_at BETWEEN :startDateTime AND :endDateTime
                        """, nativeQuery = true)
        Double sumCompletedRideRevenueBetween(@Param("startDateTime") LocalDateTime startDateTime,
                        @Param("endDateTime") LocalDateTime endDateTime);

        @Query(value = """
                        SELECT r.status::text AS status, COUNT(*) AS cnt
                        FROM rides r
                        WHERE r.requested_at BETWEEN :startDateTime AND :endDateTime
                        GROUP BY r.status
                        """, nativeQuery = true)
        List<Object[]> countRidesByStatusBetween(@Param("startDateTime") LocalDateTime startDateTime,
                        @Param("endDateTime") LocalDateTime endDateTime);

        @Query(value = "SELECT name, vehicle_details->>'vehicleType' AS vehicleType FROM drivers WHERE id = :driverId", nativeQuery = true)
        Object[] findDriverDetailsById(@Param("driverId") Long driverId);

        // --- §5 summary/count queries ---

        @Query(value = "SELECT COUNT(*) FROM rides WHERE user_id = :userId", nativeQuery = true)
        long countTotalRidesByUserId(@Param("userId") Long userId);

        // Spec: completed-count = status IN (COMPLETED, PAID)
        @Query(value = "SELECT COUNT(*) FROM rides WHERE user_id = :userId AND status IN ('COMPLETED', 'PAID')", nativeQuery = true)
        long countCompletedRidesByUserId(@Param("userId") Long userId);

        @Query(value = "SELECT COUNT(*) FROM rides WHERE user_id = :userId AND status = 'CANCELLED'", nativeQuery = true)
        long countCancelledRidesByUserId(@Param("userId") Long userId);

        // Spec: totalSpent = fare sum for COMPLETED or PAID rides
        @Query(value = "SELECT COALESCE(SUM(fare), 0) FROM rides WHERE user_id = :userId AND status IN ('COMPLETED', 'PAID')", nativeQuery = true)
        Double sumFareByUserId(@Param("userId") Long userId);

        // Spec: active-count for user = REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED,
        // PAYMENT_PENDING
        @Query(value = "SELECT COUNT(*) FROM rides WHERE user_id = :userId AND status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'PAYMENT_PENDING')", nativeQuery = true)
        int countActiveRidesByUserId(@Param("userId") Long userId);

        // Spec: driver summary totalRides = completed rides (COMPLETED or PAID) in
        // optional date range
        @Query(value = "SELECT COUNT(*) FROM rides WHERE driver_id = :driverId AND status IN ('COMPLETED', 'PAID')" +
                        " AND (CAST(:startDate AS timestamp) IS NULL OR requested_at >= :startDate)" +
                        " AND (CAST(:endDate AS timestamp) IS NULL OR requested_at <= :endDate)", nativeQuery = true)
        long countCompletedRidesByDriverIdInRange(@Param("driverId") Long driverId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query(value = "SELECT COALESCE(SUM(fare), 0) FROM rides WHERE driver_id = :driverId AND status IN ('COMPLETED', 'PAID')"
                        +
                        " AND (CAST(:startDate AS timestamp) IS NULL OR requested_at >= :startDate)" +
                        " AND (CAST(:endDate AS timestamp) IS NULL OR requested_at <= :endDate)", nativeQuery = true)
        Double sumFareByDriverIdInRange(@Param("driverId") Long driverId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // Spec: active-count for driver = ACCEPTED, IN_PROGRESS, COMPLETED,
        // PAYMENT_PENDING (no REQUESTED)
        @Query(value = "SELECT COUNT(*) FROM rides WHERE driver_id = :driverId AND status IN ('ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'PAYMENT_PENDING')", nativeQuery = true)
        int countActiveRidesByDriverId(@Param("driverId") Long driverId);

        // Spec: completed-count for driver = COMPLETED or PAID
        @Query(value = "SELECT COUNT(*) FROM rides WHERE driver_id = :driverId AND status IN ('COMPLETED', 'PAID')", nativeQuery = true)
        long countCompletedRidesByDriverId(@Param("driverId") Long driverId);
}