package com.team10.uber.user.repository;


import com.team10.uber.user.enums.Role;
import com.team10.uber.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
        @Query(value = "SELECT * FROM users u WHERE " +
                "(:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
                "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
                "(:role IS NULL OR u.role = :role)",
                nativeQuery = true)
        List<User> searchUsers(@Param("name") String name,
                               @Param("email") String email,
                               @Param("role") String role);
}