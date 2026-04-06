package com.team10.uber.ride.repository;

import com.team10.uber.ride.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideRepository extends JpaRepository<Ride, Long> {
}