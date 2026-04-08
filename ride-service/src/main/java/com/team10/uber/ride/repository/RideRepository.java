package com.team10.uber.ride.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team10.uber.ride.model.Ride;

public interface RideRepository extends JpaRepository<Ride, Long> {
	List<Ride> findByRequestedAtBetween(LocalDateTime startDateTime, LocalDateTime endDateTime);
}