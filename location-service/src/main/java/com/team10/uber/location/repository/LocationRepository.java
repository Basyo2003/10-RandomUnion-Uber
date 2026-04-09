package com.team10.uber.location.repository;

import com.team10.uber.location.dto.DriverMovementSummaryProjection;
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

    // S4-F1
    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);

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

    // S4-F8
    @Query(value = "SELECT EXISTS (SELECT 1 FROM drivers WHERE id = :driverId)", nativeQuery = true)
    boolean driverExists(@Param("driverId") Long driverId);

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
}