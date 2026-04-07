package com.team10.uber.location.repository;

import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;


import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    // For feature S4-F1 (latest location)
    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);

    @Query(value = """
        SELECT l.*
        FROM locations l
        INNER JOIN (
            SELECT driver_id, MAX(timestamp) AS max_timestamp
            FROM locations
            GROUP BY driver_id
        ) latest
        ON l.driver_id = latest.driver_id
        AND l.timestamp = latest.max_timestamp
        """, nativeQuery = true)
    List<Location> findLatestLocationPerDriver();

}