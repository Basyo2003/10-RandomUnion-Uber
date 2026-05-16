package com.team10.uber.driver.repository;

import com.team10.uber.driver.model.Driver;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Transactional
public interface DriverRepository extends JpaRepository<Driver, Long> {

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Driver d
            SET d.rating = (d.rating * d.totalRatings + :newRating) / (d.totalRatings + 1),
                d.totalRatings = d.totalRatings + 1
            WHERE d.id = :driverId
            """)
    int applyRatingUpdate(@Param("driverId") Long driverId, @Param("newRating") Double newRating);

    @Query(value = """
            SELECT d.id, d.name, d.rating
            FROM drivers d
            ORDER BY d.rating DESC, d.total_ratings DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopRatedDriverCandidates(@Param("limit") int limit);

    @Query(value = """
            SELECT * FROM drivers
            WHERE (:status IS NULL OR CAST(status AS TEXT) = :status)
              AND (:minRating IS NULL OR rating >= :minRating)
              AND (:maxRating IS NULL OR rating <= :maxRating)
            ORDER BY rating DESC
            """, nativeQuery = true)
    List<Driver> searchDrivers(
            @Param("status") String status,
            @Param("minRating") Double minRating,
            @Param("maxRating") Double maxRating);

    @Query(value = """
            SELECT * FROM drivers
            WHERE LOWER(COALESCE((CAST(vehicle_details AS jsonb) ->> 'vehicleType'), (CAST(vehicle_details AS jsonb) ->> 'type'))) = LOWER(:type)
            AND (:status IS NULL OR status = :status)
            """, nativeQuery = true)
    List<Driver> findByVehicleType(
            @Param("type") String type,
            @Param("status") String status);

    default List<Driver> findByVehicleType(String type) {
        return findByVehicleType(type, null);
    }

    default List<Driver> findByVehicleTypeAndStatus(String type, String status) {
        return findByVehicleType(type, status);
    }
}
