package com.team10.uber.location.repository;

import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    // For feature S4-F1 (latest location)
    Optional<Location> findTopByDriverIdOrderByTimestampDesc(Long driverId);

    default void driverExists(JdbcTemplate jdbcTemplate, Long driverId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE id = ?",
                Integer.class,
                driverId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Driver not found"
            );
        }
    }
}