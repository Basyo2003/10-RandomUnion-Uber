package com.team10.uber.driver.repository;

import com.team10.uber.driver.dto.TopRatedDriverDTO;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Transactional
public interface DriverRepository extends JpaRepository<Driver, Long> {

        interface EarningsAggregate {
                Long getTotalRides();

                Double getTotalEarnings();
        }

        @Query(value = """
                        SELECT
                          COUNT(*) AS totalRides,
                          COALESCE(SUM(fare), 0) AS totalEarnings
                        FROM rides
                        WHERE driver_id = :driverId
                          AND status = 'COMPLETED'
                          AND completed_at >= :startTs
                          AND completed_at < :endExclusive
                        """, nativeQuery = true)
        EarningsAggregate getEarningsAggregate(
                        @Param("driverId") Long driverId,
                        @Param("startTs") LocalDateTime startTs,
                        @Param("endExclusive") LocalDateTime endExclusive);

        @Query("""
                        SELECT d
                        FROM Driver d
                        WHERE (:status IS NULL OR d.status = :status)
                          AND (:minRating IS NULL OR d.rating >= :minRating)
                          AND (:maxRating IS NULL OR d.rating <= :maxRating)
                        ORDER BY d.rating DESC
                        """)
        List<Driver> searchDrivers(
                        @Param("status") DriverStatus status,
                        @Param("minRating") Double minRating,
                        @Param("maxRating") Double maxRating);

        @Query(value = "SELECT d.* FROM drivers d " +
                        "INNER JOIN rides r ON d.id = r.driver_id " +
                        "WHERE r.id = :rideId", nativeQuery = true)
        Optional<Driver> findDriverByRideId(@Param("rideId") Long rideId);

        // Get ride status directly as a String — no Ride class needed
        @Query(value = "SELECT status FROM rides WHERE id = :rideId", nativeQuery = true)
        Optional<String> findRideStatusById(@Param("rideId") Long rideId);

        @Query(value = """
                        SELECT * FROM drivers
                        WHERE LOWER((CAST(vehicle_details AS jsonb) ->> 'type')) = LOWER(:type)
                        AND (:status IS NULL OR status = :status)
                        """, nativeQuery = true)
        List<Driver> findByVehicleType(
                        @Param("type") String type,
                        @Param("status") String status);

        @Query(value = """
                            SELECT d.id, d.name, d.rating, COUNT(r.id)
                            FROM drivers d
                            LEFT JOIN rides r
                                ON d.id = r.driver_id
                                AND r.status = 'COMPLETED'
                            GROUP BY d.id, d.name, d.rating
                            ORDER BY d.rating DESC, COUNT(r.id) DESC
                            LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findTopDriversWithRideCount(@Param("limit") int limit);
}
