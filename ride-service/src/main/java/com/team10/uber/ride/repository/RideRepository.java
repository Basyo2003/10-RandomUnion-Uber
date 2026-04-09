package com.team10.uber.ride.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.team10.uber.ride.model.Ride;

public interface RideRepository extends JpaRepository<Ride, Long> {

    // 0) Check if driver exists by id
    @Query(value = "SELECT COUNT(1) FROM drivers WHERE id = :driverId", nativeQuery = true)
    int countDriverById(@Param("driverId") Long driverId);

    // 0.1) Check if driver is BUSY
    @Query(value = "SELECT COUNT(1) FROM drivers WHERE id = :driverId AND status = 'BUSY'", nativeQuery = true)
    int countBusyDriverById(@Param("driverId") Long driverId);

    // 1) Update driver status to AVAILABLE
    @Modifying
    @Transactional
    @Query(value = "UPDATE drivers SET status = 'AVAILABLE' WHERE id = :driverId AND status = 'BUSY'", nativeQuery = true)
    int updateDriverStatusToAvailable(@Param("driverId") Long driverId);


    // 2) Insert payment record
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO payments (ride_id, user_id, amount, method, status, transaction_details, created_at)
        VALUES (:rideId, :userId, :amount, 'CASH', 'PENDING', '{}'::jsonb, CURRENT_TIMESTAMP)
        """, nativeQuery = true)
    void createPayment(
            @Param("rideId") Long rideId,
            @Param("userId") Long userId,
            @Param("amount") Double amount
    );
}