package com.team10.uber.user.repository;


import com.team10.uber.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query(value = """
            SELECT COUNT(*) > 0
            FROM rides
            WHERE user_id = :userId
              AND status NOT IN ('COMPLETED', 'CANCELLED')
            """, nativeQuery = true)
    boolean hasActiveRides(@Param("userId") Long userId);
}
