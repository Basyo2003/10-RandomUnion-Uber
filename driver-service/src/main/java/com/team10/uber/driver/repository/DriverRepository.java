package com.team10.uber.driver.repository;

import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
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
               AND requested_at >= :startTs
            AND requested_at < :endExclusive
                  """, nativeQuery = true)
    EarningsAggregate getEarningsAggregate(
            @Param("driverId") Long driverId,
            @Param("startTs") LocalDateTime startTs,
            @Param("endExclusive") LocalDateTime endExclusive);

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

    @Query(value = "SELECT d.* FROM drivers d " +
            "INNER JOIN rides r ON d.id = r.driver_id " +
            "WHERE r.id = :rideId", nativeQuery = true)
    Optional<Driver> findDriverByRideId(@Param("rideId") Long rideId);

    // Get ride status directly as a String — no Ride class needed
    @Query(value = "SELECT status FROM rides WHERE id = :rideId", nativeQuery = true)
    Optional<String> findRideStatusById(@Param("rideId") Long rideId);

    @Query(value = """
            SELECT * FROM drivers
            WHERE LOWER(COALESCE((CAST(vehicle_details AS jsonb) ->> 'vehicleType'), (CAST(vehicle_details AS jsonb) ->> 'type'))) = LOWER(:type)
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

    @Query(value = """
            SELECT COUNT(*)
            FROM rides
            WHERE driver_id = :driverId
            AND status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS')
            """, nativeQuery = true)
    int countActiveRides(@Param("driverId") Long driverId);

    /** boolean alias for countActiveRides — used by tests */
    default boolean hasActiveRides(Long driverId) {
        return countActiveRides(driverId) > 0;
    }

    @Modifying
    @Query("UPDATE Driver d SET d.rating = (d.rating * d.totalRatings + :newRating) / (d.totalRatings + 1), " +
           "d.totalRatings = d.totalRatings + 1 WHERE d.id = :driverId")
    int applyRating(@Param("driverId") Long driverId, @Param("newRating") Double newRating);

    /** Single-arg overload — no status filter */
    default List<Driver> findByVehicleType(String type) {
        return findByVehicleType(type, null);
    }

    /** Named alias matching test expectations */
    default List<Driver> findByVehicleTypeAndStatus(String type, String status) {
        return findByVehicleType(type, status);
    }

    interface DashboardAggregate {
        Long getTotalRides();
        Double getTotalEarnings();
        Double getAverageRideFare();
    }

    @Query(value = """
            SELECT
                COUNT(r.id)                AS totalRides,
                COALESCE(SUM(p.amount), 0) AS totalEarnings,
                COALESCE(AVG(p.amount), 0) AS averageRideFare
            FROM rides r
            LEFT JOIN payments p ON p.ride_id = r.id
            WHERE r.driver_id = :driverId
              AND r.status = 'COMPLETED'
            """, nativeQuery = true)
    DashboardAggregate getDashboardAggregate(@Param("driverId") Long driverId);
}
