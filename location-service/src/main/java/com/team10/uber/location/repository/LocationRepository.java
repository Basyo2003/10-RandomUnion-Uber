package com.team10.uber.location.repository;

import com.team10.uber.location.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;


import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

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

    //S4-F3
    @Query(value = """
        SELECT l.driver_id, d.name, l.latitude, l.longitude
        FROM locations l
        JOIN drivers d ON d.id = l.driver_id
        WHERE d.status = 'AVAILABLE'
          AND l.timestamp = (
              SELECT MAX(l2.timestamp)
              FROM locations l2
              WHERE l2.driver_id = l.driver_id
          )
        """, nativeQuery = true)
    List<Object[]> findLatestAvailableDriverLocations();

}