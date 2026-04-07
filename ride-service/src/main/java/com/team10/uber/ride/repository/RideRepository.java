package com.team10.uber.ride.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team10.uber.ride.model.Ride;

public interface RideRepository extends JpaRepository<Ride, Long> {

	@Query(value = "SELECT COUNT(*) FROM drivers WHERE id = :driverId", nativeQuery = true)
	int countDriversById(@Param("driverId") Long driverId);

	@Modifying
	@Query(value = "UPDATE drivers SET status = 'AVAILABLE' WHERE id = :driverId", nativeQuery = true)
	int updateDriverStatusToAvailable(@Param("driverId") Long driverId);
}