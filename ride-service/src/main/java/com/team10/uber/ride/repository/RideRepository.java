package com.team10.uber.ride.repository;

import com.team10.uber.ride.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideRepository extends JpaRepository<Ride, Long> {

    @Query(value = "SELECT COUNT(*) FROM rides WHERE pickup_latitude BETWEEN :minLat AND :maxLat AND pickup_longitude BETWEEN :minLon AND :maxLon AND status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS')", nativeQuery = true)
    int countActiveRidesNearPickup(@Param("minLat") Double minLat, @Param("maxLat") Double maxLat,
                                   @Param("minLon") Double minLon, @Param("maxLon") Double maxLon);
}