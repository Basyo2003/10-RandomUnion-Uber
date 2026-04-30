package com.team10.uber.location.service;

import com.team10.uber.location.dto.BatchLocationRequest;
import com.team10.uber.location.dto.DriverMovementSummaryDTO;
import com.team10.uber.location.dto.DriverMovementSummaryProjection;
import com.team10.uber.location.dto.StationaryDriverDTO;
import com.team10.uber.location.event.LocationEventActions;
import com.team10.uber.location.dto.*;
import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.time.Duration;
@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RedisCacheService redisCacheService;

    private static final String CACHE_PREFIX = "location-service";

    public LocationService(LocationRepository locationRepository, JdbcTemplate jdbcTemplate , RedisCacheService redisCacheService) {
        this.locationRepository = locationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.redisCacheService = redisCacheService;
    }

    // 4.4.4 Cache Invalidation - create location invalidates feature caches
    public Location createLocation(Location location) {
        locationRepository.driverExists(jdbcTemplate, location.getDriverId());
        Location saved = locationRepository.save(location);
        invalidateLocationCaches(saved.getId(), saved.getDriverId());
        notifyObservers(LocationEventActions.LOCATION_UPDATED, saved);

        return saved;
    }

    // 4.4.2 CRUD get-by-ID cache - GET /api/locations/{id}, TTL 15 min
    public Location getLocationById(Long id) {
        String cacheKey = locationKey(id);

        Object cached = redisCacheService.get(cacheKey);
        if (cached instanceof Location location) {
            return location;
        }

        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Location not found"
                ));

        redisCacheService.set(cacheKey, location, Duration.ofMinutes(15));
        return location;
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

        Location saved = locationRepository.save(existing);
        invalidateLocationCaches(saved.getId(), saved.getDriverId());
        notifyObservers(LocationEventActions.LOCATION_UPDATED, saved);
        return saved;
    }

    // 4.4.4 Cache Invalidation - delete location clears detail and feature caches
    public void deleteLocation(Long id) {
        Location existing = getLocationById(id);
        locationRepository.delete(existing);
        invalidateLocationCaches(existing.getId(), existing.getDriverId());
        notifyObservers(LocationEventActions.LOCATION_DELETED, existing);

    }
    

    // S4-F1 - 4.4.1 Redis cache, TTL 5 min
    public Location getLatestLocationByDriverId(Long driverId) {
        locationRepository.driverExists(jdbcTemplate, driverId);

        String cacheKey = featureKey("S4-F1", driverId);

        Object cached = redisCacheService.get(cacheKey);
        if (cached instanceof Location location) {
            return location;
        }

        Location location = locationRepository.findTopByDriverIdOrderByTimestampDesc(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No locations found for driver with id: " + driverId
                ));

        redisCacheService.set(cacheKey, location, Duration.ofMinutes(5));
        return location;
    }

    // S4-F2 - 4.4.4 Cache invalidation after location write
    public Location createLocationForDriver(Long driverId, Location location) {
        locationRepository.driverExists(jdbcTemplate, driverId);

        location.setDriverId(driverId);

        Location saved = locationRepository.save(location);
        invalidateLocationCaches(saved.getId(), saved.getDriverId());
        notifyObservers(LocationEventActions.LOCATION_UPDATED, saved);

        return saved;
    }


    // S4-F3 - 4.4.1 Redis cache, TTL 10 min
    public List<NearbyDriverDTO> findNearbyAvailableDrivers(Double lat, Double lon, Double radiusKm) {
        String cacheKey = featureKey("S4-F3", lat, lon, radiusKm);

        Object cached = redisCacheService.get(cacheKey);
        if (cached instanceof List<?> cachedList) {
            return (List<NearbyDriverDTO>) cachedList;
        }

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

        redisCacheService.set(cacheKey, result, Duration.ofMinutes(10));
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

    locationRepository.driverExists(jdbcTemplate, request.getDriverId());

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

    List<Location> savedLocations = locationRepository.saveAll(locationsToSave);

    // 4.4.4 Cache Invalidation - S4-F4 batch create invalidates affected location feature caches
    for (Location saved : savedLocations) {
        invalidateLocationCaches(saved.getId(), saved.getDriverId());
    }

    Map<String, Object> response = new HashMap<>();
    response.put("count", savedLocations.size());
    response.put("message", "Batch locations created successfully");
    response.put("driverId", request.getDriverId());

    notifyObservers(
            LocationEventActions.BATCH_LOCATIONS_UPDATED,
            Map.of(
                    "driverId", request.getDriverId(),
                    "count", savedLocations.size(),
                    "firstTimestamp", savedLocations.get(0).getTimestamp(),
                    "lastTimestamp", savedLocations.get(savedLocations.size() - 1).getTimestamp()
            )
    );

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


    // S4-F5 - 4.4.1 Redis cache, TTL 5 min
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

        String normalizedOperator = operator.toLowerCase();
        String cacheKey = featureKey("S4-F5", key, normalizedOperator, value);

        Object cached = redisCacheService.get(cacheKey);
        if (cached instanceof List<?> cachedList) {
            return (List<Location>) cachedList;
        }

        List<Location> result = switch (normalizedOperator) {
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

        redisCacheService.set(cacheKey, result, Duration.ofMinutes(5));
        return result;
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



    // S4-F6 - 4.4.1 Redis cache, TTL 10 min
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

        String cacheKey = featureKey("S4-F6", startDate, endDate, driverId);

        Object cached = redisCacheService.get(cacheKey);
        if (cached instanceof List<?> cachedList) {
            return (List<Location>) cachedList;
        }

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        List<Location> result;

        if (driverId != null) {
            result = locationRepository
                    .findByDriverIdAndTimestampBetweenOrderByTimestampAsc(driverId, startDateTime, endDateTime);
        } else {
            result = locationRepository
                    .findByTimestampBetweenOrderByTimestampAsc(startDateTime, endDateTime);
        }

        redisCacheService.set(cacheKey, result, Duration.ofMinutes(10));
        return result;
    }
      
    // S4-F7 - 4.4.4 Cache Invalidation after purging old locations
    @Transactional
    public int purgeOldLocations(int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);

        int count = locationRepository.countLocationsOlderThan(cutoff);
        locationRepository.deleteLocationsOlderThan(cutoff);
      
      // Purge can affect many drivers/locations, so clear all Location Service feature caches
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::location::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F1::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F3::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F5::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F6::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F8::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F9::*");


        notifyObservers(
                LocationEventActions.OLD_LOCATIONS_PURGED,
                Map.of(
                        "count", count,
                        "olderThanDays", olderThanDays,
                        "cutoff", cutoff
                )
        );
        
        return count;
    }

    // S4-F8 - 4.4.1 Redis cache, TTL 15 min
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

    String cacheKey = featureKey("S4-F8", driverId, startDate, endDate);

    Object cached = redisCacheService.get(cacheKey);
    if (cached instanceof DriverMovementSummaryDTO summaryDTO) {
        return summaryDTO;
    }

    LocalDateTime startDateTime = startDate.atStartOfDay();
    LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

    Optional<DriverMovementSummaryProjection> optionalSummary =
            locationRepository.getDriverMovementSummary(driverId, startDateTime, endDateTime);

    DriverMovementSummaryDTO result;

    if (optionalSummary.isEmpty()) {
        result = new DriverMovementSummaryDTO(
                driverId,
                0L,
                0.0,
                0.0,
                null,
                null
        );
    } else {
        DriverMovementSummaryProjection summary = optionalSummary.get();

        result = new DriverMovementSummaryDTO(
                summary.getDriverId(),
                summary.getTotalLocationPoints(),
                summary.getAverageSpeed(),
                summary.getMaxSpeed(),
                summary.getFirstTimestamp(),
                summary.getLastTimestamp()
        );
    }

    redisCacheService.set(cacheKey, result, Duration.ofMinutes(15));

    Map<String, Object> eventData = new HashMap<>();
    eventData.put("driverId", driverId);
    eventData.put("startDate", startDate);
    eventData.put("endDate", endDate);
    eventData.put("totalLocationPoints", result.getTotalLocationPoints());
    eventData.put("averageSpeed", result.getAverageSpeed());
    eventData.put("maxSpeed", result.getMaxSpeed());
    eventData.put("firstTimestamp", result.getFirstTimestamp());
    eventData.put("lastTimestamp", result.getLastTimestamp());

    notifyObservers(
            LocationEventActions.ANALYTICS_VIEWED,
            eventData
    );

    return result;
}

    // S4-F9 - 4.4.1 Redis cache, TTL 10 min
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

    String cacheKey = featureKey("S4-F9", maxSpeed, sinceMinutes);

    Object cached = redisCacheService.get(cacheKey);
    if (cached instanceof List<?> cachedList) {
        return (List<StationaryDriverDTO>) cachedList;
    }

    LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(sinceMinutes);

    List<StationaryDriverDTO> result = locationRepository.findStationaryDrivers(maxSpeed, cutoffTime)
            .stream()
            .map(p -> StationaryDriverDTO.builder()
                    .driverId(p.getDriverId())
                    .driverName(p.getDriverName())
                    .latitude(p.getLatitude())
                    .longitude(p.getLongitude())
                    .lastSpeed(p.getLastSpeed())
                    .lastUpdated(p.getLastUpdated())
                    .build())
            .toList();

    redisCacheService.set(cacheKey, result, Duration.ofMinutes(10));

    notifyObservers(
            LocationEventActions.ANALYTICS_VIEWED,
            Map.of(
                    "analyticsType", "STATIONARY_DRIVERS",
                    "maxSpeed", maxSpeed,
                    "sinceMinutes", sinceMinutes,
                    "resultCount", result.size()
            )
    );

    return result;
}


    // 4.4 Redis Caching - cache key helpers
    private String locationKey(Long id) {
        return CACHE_PREFIX + "::location::" + id;
    }

    private String featureKey(String featureId, Object... params) {
        StringBuilder key = new StringBuilder(CACHE_PREFIX + "::" + featureId);

        for (Object param : params) {
            key.append("::").append(param == null ? "all" : param.toString());
        }

        return key.toString();
    }

    // 4.4.4 Cache Invalidation - clear Location Service feature caches affected by location writes
    private void invalidateLocationCaches(Long locationId, Long driverId) {
        if (locationId != null) {
            redisCacheService.delete(locationKey(locationId));
        }

        if (driverId != null) {
            redisCacheService.delete(featureKey("S4-F1", driverId));
            redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F8::" + driverId + "::*");
        }

        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F3::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F5::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F6::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F9::*");
    }
   


}
