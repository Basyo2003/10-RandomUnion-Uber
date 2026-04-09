package com.team10.uber.location.repository;

import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    // For feature S4-F1 (latest location)
    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);
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
}

