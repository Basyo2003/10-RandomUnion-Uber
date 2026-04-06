package com.team10.uber.ride.repository;

import com.team10.uber.ride.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {

    @Query(value = "SELECT * FROM rides WHERE metadata ->> :key = :value", nativeQuery = true)
    List<Ride> findByMetadataField(@Param("key") String key, @Param("value") String value);
}