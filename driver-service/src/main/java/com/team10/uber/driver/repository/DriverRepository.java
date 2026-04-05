package com.team10.uber.driver.repository;

import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional
public interface DriverRepository extends JpaRepository<Driver, Long> {
    @Query("""
            SELECT d
            FROM Driver d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:minRating IS NULL OR d.rating >= :minRating)
              AND (:maxRating IS NULL OR d.rating <= :maxRating)
            ORDER BY d.rating DESC
            """)
    List<Driver> searchDrivers(
            @Param("status") DriverStatus status,
            @Param("minRating") Double minRating,
            @Param("maxRating") Double maxRating);

    @Query(value = "SELECT d.* FROM drivers d " +
            "INNER JOIN rides r ON d.id = r.driver_id " +
            "WHERE r.id = :rideId", nativeQuery = true)
    Optional<Driver> findDriverByRideId(@Param("rideId") Long rideId);

    // Get ride status directly as a String — no Ride class needed
    @Query(value = "SELECT status FROM rides WHERE id = :rideId", nativeQuery = true)
    Optional<String> findRideStatusById(@Param("rideId") Long rideId);
}
