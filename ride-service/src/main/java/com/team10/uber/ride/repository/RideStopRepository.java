package com.team10.uber.ride.repository;

import com.team10.uber.ride.model.RideStop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideStopRepository extends JpaRepository<RideStop, Long> {
}