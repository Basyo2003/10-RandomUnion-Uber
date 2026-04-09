package com.team10.uber.location.repository;

import com.team10.uber.location.dto.DriverMovementSummaryProjection;
import com.team10.uber.location.dto.StationaryDriverDTO;
import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

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

    
    // S4-F1
    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);
  
  
  //S4-F3
    @Query(value = """
        SELECT l.driver_id, d.name, l.latitude, l.longitude
        FROM locations l
        JOIN drivers d ON d.id = l.driver_id
        WHERE d.status = 'AVAILABLE'
          AND l.timestamp = (
              SELECT MAX(l2.timestamp)
              FROM locations l2
              WHERE l2.driver_id = l.driver_id
          )
        """, nativeQuery = true)
    List<Object[]> findLatestAvailableDriverLocations();

    

    // S4-F5
    @Query(value = """
            SELECT * FROM locations
            WHERE metadata ->> :key = :value
            """, nativeQuery = true)
    List<Location> findByMetadataEq(
            @Param("key") String key,
            @Param("value") String value
    );

    // S4-F5
    @Query(value = """
            SELECT * FROM locations
            WHERE CAST(metadata ->> :key AS DOUBLE PRECISION) > CAST(:value AS DOUBLE PRECISION)
            """, nativeQuery = true)
    List<Location> findByMetadataGt(
            @Param("key") String key,
            @Param("value") String value
    );

    // S4-F5
    @Query(value = """
            SELECT * FROM locations
            WHERE CAST(metadata ->> :key AS DOUBLE PRECISION) < CAST(:value AS DOUBLE PRECISION)
            """, nativeQuery = true)
    List<Location> findByMetadataLt(
            @Param("key") String key,
            @Param("value") String value
    );

    // S4-F6
    @Query("""
            SELECT l
            FROM Location l
            WHERE l.driverId = :driverId
              AND l.timestamp BETWEEN :startDate AND :endDate
            ORDER BY l.timestamp ASC
            """)
    List<Location> findByDriverIdAndTimestampBetweenOrderByTimestampAsc(
            @Param("driverId") Long driverId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
           
           
    // S4-F6
    @Query("""
            SELECT l
            FROM Location l
            WHERE l.timestamp BETWEEN :startDate AND :endDate
            ORDER BY l.timestamp ASC
            """)
    List<Location> findByTimestampBetweenOrderByTimestampAsc(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // S4-F8
    @Query(value = "SELECT EXISTS (SELECT 1 FROM drivers WHERE id = :driverId)", nativeQuery = true)
    boolean driverExists(@Param("driverId") Long driverId);

    // S4-F8
    @Query(value = """
            SELECT
                l.driver_id AS driverId,
                COUNT(*) AS totalLocationPoints,
                AVG(CAST(l.metadata->>'speed' AS DOUBLE PRECISION)) AS averageSpeed,
                MAX(CAST(l.metadata->>'speed' AS DOUBLE PRECISION)) AS maxSpeed,
                MIN(l.timestamp) AS firstTimestamp,
                MAX(l.timestamp) AS lastTimestamp
            FROM locations l
            WHERE l.driver_id = :driverId
              AND l.timestamp >= :startDateTime
              AND l.timestamp < :endDateTime
            GROUP BY l.driver_id
            """, nativeQuery = true)
    Optional<DriverMovementSummaryProjection> getDriverMovementSummary(
            @Param("driverId") Long driverId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // S4-F9
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