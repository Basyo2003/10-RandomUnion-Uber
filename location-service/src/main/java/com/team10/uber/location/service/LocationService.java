package com.team10.uber.location.service;

import com.team10.uber.location.dto.BatchLocationRequest;
import com.team10.uber.location.dto.DriverMovementSummaryDTO;
import com.team10.uber.location.dto.DriverMovementSummaryProjection;
import com.team10.uber.location.dto.StationaryDriverDTO;
import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.location.dto.NearbyDriverDTO;
import java.util.ArrayList;
import java.util.Comparator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;

    public LocationService(LocationRepository locationRepository, JdbcTemplate jdbcTemplate) {
        this.locationRepository = locationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Location createLocation(Location location) {
        locationRepository.driverExists(jdbcTemplate,location.getDriverId());
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
        Location existing = locationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Location not found"
                ));

        if (updated.getDriverId() != null) {
            locationRepository.driverExists(jdbcTemplate, updated.getDriverId());
            existing.setDriverId(updated.getDriverId());
        }

        if (updated.getLatitude() != null) {
            existing.setLatitude(updated.getLatitude());
        }

        if (updated.getLongitude() != null) {
            existing.setLongitude(updated.getLongitude());
        }

        if (updated.getTimestamp() != null) {
            existing.setTimestamp(updated.getTimestamp());
        }

        if (updated.getMetadata() != null) {
            existing.setMetadata(updated.getMetadata());
        }

        return locationRepository.save(existing);
    }

    public void deleteLocation(Long id) {
        Location existing = getLocationById(id);
        locationRepository.delete(existing);
    }
    

    // S4-F1
    public Location getLatestLocationByDriverId(Long driverId) {
        locationRepository.driverExists(jdbcTemplate, driverId);

        return locationRepository.findTopByDriverIdOrderByTimestampDesc(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No locations found for driver with id: " + driverId
                ));
    }
  
  //S4-F2
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



  //S4-F4
     @Transactional
    public Map<String, Object> batchCreateLocations(BatchLocationRequest request) {
        if (request.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver ID is required");
        }

        if (request.getLocations() == null || request.getLocations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Locations list cannot be empty");
        }

        locationRepository.driverExists(jdbcTemplate,request.getDriverId());

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

    
    // S4-F5
    public List<Location> filterLocationsByMetadata(String key, String operator, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Key and value are required"
            );
        }

        if (operator == null || operator.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Operator is required"
            );
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
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid operator"
            );
        };
    }

    // S4-F5 helper
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


  
    // S4-F6
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
      
    //S4-F7
    @Transactional
    public int purgeOldLocations(int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);

        int count = locationRepository.countLocationsOlderThan(cutoff);
        locationRepository.deleteLocationsOlderThan(cutoff);

        return count;
    }

    // S4-F8
    public DriverMovementSummaryDTO getDriverMovementSummary(
            Long driverId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "endDate must be on or after startDate"
            );
        }

        locationRepository.driverExists(jdbcTemplate, driverId);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        Optional<DriverMovementSummaryProjection> optionalSummary =
                locationRepository.getDriverMovementSummary(driverId, startDateTime, endDateTime);

        if (optionalSummary.isEmpty()) {
            return new DriverMovementSummaryDTO(
                    driverId,
                    0L,
                    0.0,
                    0.0,
                    null,
                    null
            );
        }

        DriverMovementSummaryProjection summary = optionalSummary.get();

        return new DriverMovementSummaryDTO(
                summary.getDriverId(),
                summary.getTotalLocationPoints(),
                summary.getAverageSpeed(),
                summary.getMaxSpeed(),
                summary.getFirstTimestamp(),
                summary.getLastTimestamp()
        );
    }

    // S4-F9
    public List<StationaryDriverDTO> getStationaryDrivers(Double maxSpeed, Integer sinceMinutes) {
        if (maxSpeed == null || maxSpeed < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "maxSpeed must be >= 0"
            );
        }

        if (sinceMinutes == null || sinceMinutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sinceMinutes must be > 0"
            );
        }

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(sinceMinutes);
        return locationRepository.findStationaryDrivers(maxSpeed, cutoffTime);
    }
   


}
