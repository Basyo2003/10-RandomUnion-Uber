
package com.team10.uber.location.repository;

import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);

    @Query(value = """
            SELECT * FROM locations
            WHERE metadata ->> :key = :value
            """, nativeQuery = true)
    List<Location> findByMetadataEq(@Param("key") String key, @Param("value") String value);

    @Query(value = """
            SELECT * FROM locations
            WHERE CAST(metadata ->> :key AS DOUBLE PRECISION) > CAST(:value AS DOUBLE PRECISION)
            """, nativeQuery = true)
    List<Location> findByMetadataGt(@Param("key") String key, @Param("value") String value);

    @Query(value = """
            SELECT * FROM locations
            WHERE CAST(metadata ->> :key AS DOUBLE PRECISION) < CAST(:value AS DOUBLE PRECISION)
            """, nativeQuery = true)
    List<Location> findByMetadataLt(@Param("key") String key, @Param("value") String value);
}