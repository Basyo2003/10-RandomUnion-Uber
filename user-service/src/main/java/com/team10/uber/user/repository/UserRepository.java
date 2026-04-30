package com.team10.uber.user.repository;

import com.team10.uber.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    @Query(value = "SELECT * FROM users u WHERE " +
            "(:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
            "(:role IS NULL OR u.role = :role)", nativeQuery = true)
    List<User> searchUsers(@Param("name") String name,
                           @Param("email") String email,
                           @Param("role") String role);

    @Query(value = """
            SELECT
                COUNT(r.id) AS total_rides,
                COALESCE(SUM(CASE WHEN r.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_rides,
                COALESCE(SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_rides,
                COALESCE(SUM(CASE WHEN r.status = 'COMPLETED' THEN r.fare ELSE 0 END), 0) AS total_spent,
                COALESCE(AVG(CASE WHEN r.status = 'COMPLETED' THEN r.fare END), 0) AS average_fare
            FROM rides r
            WHERE r.user_id = :userId
            """, nativeQuery = true)
    List<Object[]> getRideSummaryByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT
                u.id AS userId,
                u.name AS name,
                COALESCE(SUM(CASE WHEN r.status = 'COMPLETED' THEN r.fare ELSE 0 END), 0) AS totalSpent,
                COUNT(CASE WHEN r.status = 'COMPLETED' THEN 1 END) AS rideCount
            FROM users u
            LEFT JOIN rides r ON r.user_id = u.id
            AND r.requested_at BETWEEN CAST(:startDate AS timestamp) AND CAST(:endDate AS timestamp)
            GROUP BY u.id, u.name
            HAVING SUM(CASE WHEN r.status = 'COMPLETED' THEN r.fare ELSE 0 END) > 0
            ORDER BY totalSpent DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopRidersBySpending(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("limit") int limit);

    @Query(value = "SELECT * FROM users u WHERE u.preferences ->> :key = :value", nativeQuery = true)
    List<User> searchByPreference(@Param("key") String key, @Param("value") String value);

    @Query(value = "SELECT u.* FROM users u " +
            "INNER JOIN rides r ON u.id = r.user_id " +
            "WHERE u.preferences ->> 'language' = :lang " +
            "AND r.status = 'COMPLETED' " +
            "GROUP BY u.id " +
            "HAVING COUNT(r.id) >= :minRides", nativeQuery = true)
    List<User> findUsersByLanguageAndCompletedRides(@Param("lang") String lang,
                                                    @Param("minRides") int minRides);

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM rides
            WHERE user_id = :userId
              AND status NOT IN ('COMPLETED', 'CANCELLED')
            """, nativeQuery = true)
    boolean hasActiveRides(@Param("userId") Long userId);
}
