package com.team10.uber.location.repository;

import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    // For feature S4-F1 (latest location)
    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);

}