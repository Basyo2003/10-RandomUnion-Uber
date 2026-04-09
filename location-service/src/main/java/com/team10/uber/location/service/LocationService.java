package com.team10.uber.location.service;

import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.time.LocalDate;

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


  
    //S4-F6
    public List<Location> getLocationsInDateRange(String startDate, String endDate, Long driverId) {

        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start and end dates are required");
        }

        LocalDate start;
        LocalDate end;

        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use YYYY-MM-DD");
        }

        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be after end date");
        }

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        if (driverId != null) {
            return locationRepository
                    .findByDriverIdAndTimestampBetweenOrderByTimestampAsc(driverId, startDateTime, endDateTime);
        }

        return locationRepository
                .findByTimestampBetweenOrderByTimestampAsc(startDateTime, endDateTime);
    }
}