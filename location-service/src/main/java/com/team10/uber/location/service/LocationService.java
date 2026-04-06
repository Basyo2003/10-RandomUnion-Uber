package com.team10.uber.location.service;

import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.location.dto.StationaryDriverDTO;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;

    public LocationService(LocationRepository locationRepository, JdbcTemplate jdbcTemplate) {
        this.locationRepository = locationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // CREATE
    public Location createLocation(Location location) {
        driverExists(location.getDriverId());
        return locationRepository.save(location);
    }

    // READ BY ID
    public Location getLocationById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Location not found"
                ));
    }

    // READ ALL
    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

    // UPDATE
    public Location updateLocation(Long id, Location updated) {
        Location existing = getLocationById(id);

        driverExists(updated.getDriverId());

        existing.setDriverId(updated.getDriverId());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        existing.setTimestamp(LocalDateTime.now());
        existing.setMetadata(updated.getMetadata());

        return locationRepository.save(existing);
    }

    // DELETE
    public void deleteLocation(Long id) {
        Location existing = getLocationById(id);
        locationRepository.delete(existing);

    }

    // required by spec (driver is not JPA-managed)
    private void driverExists(Long driverId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE id = ?",
                Integer.class,
                driverId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Driver not found"
            );
        }
    }

    public List<StationaryDriverDTO> getStationaryDrivers(Double maxSpeed, Integer sinceMinutes) {
        if (maxSpeed == null || maxSpeed < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "maxSpeed must be >= 0"
            );
        }

        if (sinceMinutes == null || sinceMinutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "sinceMinutes must be > 0"
            );
        }

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(sinceMinutes);
        return locationRepository.findStationaryDrivers(maxSpeed, cutoffTime);
    }
}