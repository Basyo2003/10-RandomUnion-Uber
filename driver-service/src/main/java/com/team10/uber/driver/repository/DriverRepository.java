package com.team10.uber.driver.repository;

import com.team10.uber.driver.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
public interface DriverRepository extends JpaRepository<Driver, Long> {
    @Query(value = "SELECT d.* FROM drivers d " +
            "INNER JOIN rides r ON d.id = r.driver_id " +
            "WHERE r.id = :rideId", nativeQuery = true)
    Optional<Driver> findDriverByRideId(@Param("rideId") Long rideId);

    // Get ride status directly as a String — no Ride class needed
    @Query(value = "SELECT status FROM rides WHERE id = :rideId", nativeQuery = true)
    Optional<String> findRideStatusById(@Param("rideId") Long rideId);
}
