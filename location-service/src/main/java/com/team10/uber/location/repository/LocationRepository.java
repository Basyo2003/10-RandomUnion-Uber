package com.team10.uber.location.repository;

import com.team10.uber.location.dto.StationaryDriverDTO;
import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    default void driverExists(JdbcTemplate jdbcTemplate, Long driverId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE id = ?",
                Integer.class,
                driverId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Driver not found"
            );
        }
    }

    //S4-F9
    @Query(value = """
        SELECT 
            l.driver_id AS driverId,
            d.name AS driverName,
            l.latitude AS latitude,
            l.longitude AS longitude,
            CAST(l.metadata ->> 'speed' AS DOUBLE PRECISION) AS lastSpeed,
            l.timestamp AS lastUpdated
        FROM locations l
        JOIN drivers d ON d.id = l.driver_id
        JOIN (
            SELECT driver_id, MAX(timestamp) AS max_timestamp
            FROM locations
            GROUP BY driver_id
        ) latest 
            ON l.driver_id = latest.driver_id 
           AND l.timestamp = latest.max_timestamp
        WHERE CAST(l.metadata ->> 'speed' AS DOUBLE PRECISION) <= :maxSpeed
          AND l.timestamp >= :cutoffTime
        """, nativeQuery = true)
    List<StationaryDriverDTO> findStationaryDrivers(
            @Param("maxSpeed") Double maxSpeed,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

}