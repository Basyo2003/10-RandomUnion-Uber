package com.team10.uber.ride.repository;

import com.team10.uber.ride.model.RideStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RideStopRepository extends JpaRepository<RideStop, Long> {

    @Query("SELECT COALESCE(MAX(rs.stopOrder), 0) FROM RideStop rs WHERE rs.ride.id = :rideId")
    int findMaxStopOrderByRideId(@Param("rideId") Long rideId);

    List<RideStop> findByRideIdOrderByStopOrderAsc(Long rideId);
}