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
    public Location createLocationForDriver(Long driverId, Location location) {
        driverExists(driverId);

        location.setDriverId(driverId);
        return locationRepository.save(location);
    }
    public List<NearbyDriverDTO> findNearbyAvailableDrivers(Double lat, Double lon, Double radiusKm) {
        List<Location> latestLocations = locationRepository.findLatestLocationPerDriver();
        List<NearbyDriverDTO> result = new ArrayList<>();

        for (Location location : latestLocations) {
            Long driverId = location.getDriverId();

            List<DriverInfo> driverRows = jdbcTemplate.query(
                    "SELECT id, name, status FROM drivers WHERE id = ?",
                    (rs, rowNum) -> new DriverInfo(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("status")
                    ),
                    driverId
            );

            if (driverRows.isEmpty()) {
                continue;
            }

            DriverInfo driver = driverRows.get(0);

            if (!"AVAILABLE".equals(driver.getStatus())) {
                continue;
            }

            double distanceKm = calculateDistanceKm(
                    lat, lon,
                    location.getLatitude(), location.getLongitude()
            );

            if (distanceKm <= radiusKm) {
                result.add(new NearbyDriverDTO(
                        driverId,
                        driver.getName(),
                        location.getLatitude(),
                        location.getLongitude(),
                        distanceKm
                ));
            }
        }

        result.sort(Comparator.comparing(NearbyDriverDTO::getDistanceKm));
        return result;
    }
    private double calculateDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        double dLat = lat1 - lat2;
        double dLon = lon1 - lon2;
        return Math.sqrt(dLat * dLat + dLon * dLon) * 111;
    }

    private static class DriverInfo {
        private final Long id;
        private final String name;
        private final String status;

        public DriverInfo(Long id, String name, String status) {
            this.id = id;
            this.name = name;
            this.status = status;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }
    }

}