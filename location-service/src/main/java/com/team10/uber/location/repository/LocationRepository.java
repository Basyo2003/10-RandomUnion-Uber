package com.team10.uber.location.repository;

import com.team10.uber.location.dto.DriverMovementSummaryProjection;
import com.team10.uber.location.dto.StationaryDriverDTO;
import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.location.dto.StationaryDriverProjection;

import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;


public interface LocationRepository extends JpaRepository<Location, Long> {


    /*default void driverExists(JdbcTemplate jdbcTemplate, Long driverId) {
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
    }*/

    /*@Query(value = "SELECT EXISTS (SELECT 1 FROM rides WHERE id = :rideId)", nativeQuery = true)
    boolean rideExists(@Param("rideId") Long rideId);*/

    

    
    // S4-F1
    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);


  //S4-F3
  @Query(value = """
    SELECT l.driver_id, l.latitude, l.longitude
    FROM locations l
    WHERE l.timestamp = (
        SELECT MAX(l2.timestamp)
        FROM locations l2
        WHERE l2.driver_id = l.driver_id
    )
    LIMIT 100
    """, nativeQuery = true)
  List<Object[]> findLatestDriverLocations();


    // S4-F5
    @Query(value = """
          SELECT * FROM locations
          WHERE CAST(metadata ->> :key AS DOUBLE PRECISION) = CAST(:value AS DOUBLE PRECISION)
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
           
           
           
    //S4-F7
    @Query(value = "SELECT COUNT(*) FROM locations WHERE timestamp < :cutoff", nativeQuery = true)
    int countLocationsOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query(value = "DELETE FROM locations WHERE timestamp < :cutoff", nativeQuery = true)
    int deleteLocationsOlderThan(@Param("cutoff") LocalDateTime cutoff);

    // S4-F8
    /*@Query(value = "SELECT EXISTS (SELECT 1 FROM drivers WHERE id = :driverId)", nativeQuery = true)
    boolean driverExists(@Param("driverId") Long driverId);*/

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
        l.latitude AS latitude,
        l.longitude AS longitude,
        CAST(l.metadata ->> 'speed' AS DOUBLE PRECISION) AS lastSpeed,
        l.timestamp AS lastUpdated
    FROM locations l
    JOIN (
        SELECT driver_id, MAX(timestamp) AS max_timestamp
        FROM locations
        GROUP BY driver_id
    ) latest
        ON l.driver_id = latest.driver_id
       AND l.timestamp = latest.max_timestamp
    WHERE CAST(l.metadata ->> 'speed' AS DOUBLE PRECISION) <= :maxSpeed
      AND l.timestamp >= :cutoffTime
    ORDER BY l.timestamp ASC
    LIMIT 100
    """, nativeQuery = true)
    List<StationaryDriverProjection> findStationaryDrivers(
            @Param("maxSpeed") Double maxSpeed,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );




    // S4-F10
    @Query(value = """
        SELECT COUNT(*)
        FROM locations
        WHERE timestamp >= :startDateTime
          AND timestamp <= :endDateTime
        """, nativeQuery = true)
    Long countLocationEventsBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    @Query(value = """
        SELECT COUNT(DISTINCT driver_id)
        FROM locations
        WHERE timestamp >= :startDateTime
          AND timestamp <= :endDateTime
        """, nativeQuery = true)
    Long countActiveDriversBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    @Query(value = """
        SELECT COALESCE(
            AVG(
                COALESCE(
                    CAST(NULLIF(metadata ->> 'speed', '') AS DOUBLE PRECISION),
                    0
                )
            ),
            0
        )
        FROM locations
        WHERE timestamp >= :startDateTime
          AND timestamp <= :endDateTime
        """, nativeQuery = true)
    Double findAverageSpeedBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    @Query(value = """
        SELECT CAST(EXTRACT(HOUR FROM timestamp) AS int) AS hour,
               COUNT(*) AS count
        FROM locations
        WHERE timestamp >= :startDateTime
          AND timestamp <= :endDateTime
        GROUP BY CAST(EXTRACT(HOUR FROM timestamp) AS int)
        ORDER BY hour
        """, nativeQuery = true)
    List<Object[]> countEventsByHourBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}