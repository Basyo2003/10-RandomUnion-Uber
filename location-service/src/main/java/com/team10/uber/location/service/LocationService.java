package com.team10.uber.location.service;

import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.location.dto.NearbyDriverDTO;
import java.util.ArrayList;
import java.util.Comparator;

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


    public Location createLocationForDriver(Long driverId, Location location) {
        locationRepository.driverExists(jdbcTemplate,driverId);

        location.setDriverId(driverId);
        return locationRepository.save(location);
    }
    //S4-F3
    public List<NearbyDriverDTO> findNearbyAvailableDrivers(Double lat, Double lon, Double radiusKm) {
        List<Object[]> rows = locationRepository.findLatestAvailableDriverLocations();
        List<NearbyDriverDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long driverId = ((Number) row[0]).longValue();
            String driverName = (String) row[1];
            Double driverLat = ((Number) row[2]).doubleValue();
            Double driverLon = ((Number) row[3]).doubleValue();

            double distanceKm = calculateDistanceKm(lat, lon, driverLat, driverLon);

            if (distanceKm <= radiusKm) {
                result.add(new NearbyDriverDTO(
                        driverId,
                        driverName,
                        driverLat,
                        driverLon,
                        distanceKm
                ));
            }
        }

        result.sort(Comparator.comparing(NearbyDriverDTO::getDistanceKm));
        return result;
    }

    private double calculateDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        double deltaLat = lat1 - lat2;
        double deltaLon = lon1 - lon2;
        return Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon) * 111;
    }

}