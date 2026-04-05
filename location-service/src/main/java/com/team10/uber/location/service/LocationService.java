package com.team10.uber.location.service;

import com.team10.uber.location.dto.BatchLocationRequest;
import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;

    public LocationService(LocationRepository locationRepository, JdbcTemplate jdbcTemplate) {
        this.locationRepository = locationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Location createLocation(Location location) {
        driverExists(location.getDriverId());
        return locationRepository.save(location);

    }

    public Location getLocationById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Location not found"
                ));
    }

    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

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

    public void deleteLocation(Long id) {
        Location existing = getLocationById(id);
        locationRepository.delete(existing);
    }

    @Transactional
    public Map<String, Object> batchCreateLocations(BatchLocationRequest request) {
        if (request.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver ID is required");
        }

        if (request.getLocations() == null || request.getLocations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Locations list cannot be empty");
        }

        driverExists(request.getDriverId());

        List<Location> locationsToSave = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now();

        for (int i = 0; i < request.getLocations().size(); i++) {
            BatchLocationRequest.LocationInput input = request.getLocations().get(i);

            validateCoordinates(input.getLatitude(), input.getLongitude());

            Location location = new Location();
            location.setDriverId(request.getDriverId());
            location.setLatitude(input.getLatitude());
            location.setLongitude(input.getLongitude());
            location.setMetadata(input.getMetadata());
            location.setTimestamp(baseTime.plusSeconds(i));

            locationsToSave.add(location);
        }

        locationRepository.saveAll(locationsToSave);

        Map<String, Object> response = new HashMap<>();
        response.put("count", locationsToSave.size());
        response.put("message", "Batch locations created successfully");

        return response;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude and longitude are required");
        }

        if (latitude < -90 || latitude > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude must be between -90 and 90");
        }

        if (longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Longitude must be between -180 and 180");
        }
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
}
