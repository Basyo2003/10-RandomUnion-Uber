package com.team10.uber.location.service;

import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
        locationRepository.driverExists(jdbcTemplate,location.getDriverId());
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

        locationRepository.driverExists(jdbcTemplate,updated.getDriverId());

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

    //S4-F5



    public List<Location> filterLocationsByMetadata(String key, String operator, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key and value are required");
        }

        if (operator == null || operator.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator is required");
        }

        return switch (operator.toLowerCase()) {
            case "eq" -> locationRepository.findByMetadataEq(key, value);
            case "gt" -> {
                validateNumeric(value);
                yield locationRepository.findByMetadataGt(key, value);
            }
            case "lt" -> {
                validateNumeric(value);
                yield locationRepository.findByMetadataLt(key, value);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid operator");
        };
    }
        //S4-F5
    private void validateNumeric(String value) {
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Value must be numeric for gt or lt"
            );
        }
    }
}