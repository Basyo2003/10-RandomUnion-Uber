package com.team10.uber.driver.repository;

import com.team10.uber.driver.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Transactional
public interface DriverRepository extends JpaRepository<Driver, Long> {

    interface EarningsAggregate {
        Long getTotalRides();
        Double getTotalEarnings();
    }

    @Query(value = """
            SELECT
              COUNT(*) AS totalRides,
              COALESCE(SUM(fare), 0) AS totalEarnings
            FROM rides
            WHERE driver_id = :driverId
              AND status = 'COMPLETED'
              AND completed_at >= :startTs
              AND completed_at < :endExclusive
            """, nativeQuery = true)
    EarningsAggregate getEarningsAggregate(
            @Param("driverId") Long driverId,
            @Param("startTs") LocalDateTime startTs,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query(value = "SELECT d.* FROM drivers d " +
            "INNER JOIN rides r ON d.id = r.driver_id " +
            "WHERE r.id = :rideId", nativeQuery = true)
    Optional<Driver> findDriverByRideId(@Param("rideId") Long rideId);

    // Get ride status directly as a String — no Ride class needed
    @Query(value = "SELECT status FROM rides WHERE id = :rideId", nativeQuery = true)
    Optional<String> findRideStatusById(@Param("rideId") Long rideId);
}
