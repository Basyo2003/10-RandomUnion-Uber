package com.team10.uber.ride.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.team10.uber.ride.model.Ride;

public interface RideRepository extends JpaRepository<Ride, Long> {
    @Query(value = "SELECT * FROM rides WHERE metadata ->> :key = :value", nativeQuery = true)
    List<Ride> findByMetadataField(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT COUNT(*) FROM rides WHERE pickup_latitude BETWEEN :minLat AND :maxLat AND pickup_longitude BETWEEN :minLon AND :maxLon AND status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS')", nativeQuery = true)
    int countActiveRidesNearPickup(@Param("minLat") Double minLat, @Param("maxLat") Double maxLat,
                                   @Param("minLon") Double minLon, @Param("maxLon") Double maxLon);
    List<Ride> findByRequestedAtBetween(LocalDateTime startDateTime, LocalDateTime endDateTime);
}