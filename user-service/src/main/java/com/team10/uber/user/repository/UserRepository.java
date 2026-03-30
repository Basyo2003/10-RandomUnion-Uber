package com.team10.uber.user.repository;

import com.team10.uber.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

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
}