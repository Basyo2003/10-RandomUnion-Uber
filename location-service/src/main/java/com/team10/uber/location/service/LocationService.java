package com.team10.uber.location.service;

import feign.FeignException;
import com.team10.uber.location.dto.BatchLocationRequest;
import com.team10.uber.location.dto.DriverMovementSummaryDTO;
import com.team10.uber.location.dto.DriverMovementSummaryProjection;
import com.team10.uber.location.dto.StationaryDriverDTO;
import com.team10.uber.location.event.LocationEventActions;
import com.team10.uber.location.dto.*;
import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.LocationRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.location.observer.AbstractObservableService;
import com.team10.uber.location.observer.MongoEventLogger;
import com.team10.uber.location.dto.TrackingEventRequest;
import com.team10.uber.contracts.feign.DriverServiceClient;
import com.team10.uber.contracts.dto.DriverDTO;
import java.util.Objects;

import java.time.*;
import java.util.*;

@Service
public class LocationService extends AbstractObservableService {

    private final LocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MongoEventLogger mongoEventLogger;
    private final LocationTrackingEventService locationTrackingEventService;
    private final RedisCacheService redisCacheService;
    private final DriverServiceClient driverServiceClient;

    private static final String CACHE_PREFIX = "location-service";

    public LocationService(
        LocationRepository locationRepository,
        JdbcTemplate jdbcTemplate,
        RedisCacheService redisCacheService,
        MongoEventLogger mongoEventLogger,
        LocationTrackingEventService locationTrackingEventService,
        DriverServiceClient driverServiceClient
) {
    this.locationRepository = locationRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.redisCacheService = redisCacheService;
    this.mongoEventLogger = mongoEventLogger;
    this.locationTrackingEventService = locationTrackingEventService;
    this.driverServiceClient = driverServiceClient;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    // 4.4.4 Cache Invalidation - create location invalidates feature caches
    public Location createLocation(Location location) {
        //locationRepository.driverExists(jdbcTemplate, location.getDriverId());
        Location saved = locationRepository.save(location);
        locationTrackingEventService.recordTrackingEvent(saved);
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
            //locationRepository.driverExists(jdbcTemplate, updated.getDriverId());
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
        ///new line 
        locationTrackingEventService.recordTrackingEvent(saved);
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
        checkDriverExists(driverId);

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
        checkDriverExists(driverId);

        validateCoordinates(location.getLatitude(), location.getLongitude());

        location.setDriverId(driverId);

        Location saved = locationRepository.save(location);
        locationTrackingEventService.recordTrackingEvent(saved);
        invalidateLocationCaches(saved.getId(), saved.getDriverId());
        notifyObservers(LocationEventActions.LOCATION_UPDATED, saved);

        return saved;
    }

    //S4-F3
    public List<NearbyDriverDTO> findNearbyAvailableDrivers(Double lat, Double lon, Double radiusKm, int page, int size) {
        validateCoordinates(lat, lon);

        if (radiusKm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm is required");
        }

        if (radiusKm < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm must be >= 0");
        }

        if (radiusKm == 0) {
            return List.of();
        }

        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        }

        if (size <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be > 0");
        }

        size = Math.min(size, 100);

        String cacheKey = featureKey("S4-F3", lat, lon, radiusKm, page, size);

        Object cached = redisCacheService.get(cacheKey);
        if (cached instanceof List<?> cachedList) {
            return (List<NearbyDriverDTO>) cachedList;
        }

        List<Object[]> rows = locationRepository.findLatestDriverLocations();
        List<NearbyDriverDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long driverId = ((Number) row[0]).longValue();
            Double driverLat = ((Number) row[1]).doubleValue();
            Double driverLon = ((Number) row[2]).doubleValue();

            double distanceKm = calculateDistanceKm(lat, lon, driverLat, driverLon);

            if (distanceKm > radiusKm) {
                continue;
            }

            DriverDTO driver;
            try {
                driver = driverServiceClient.getDriver(driverId);
            } catch (FeignException.NotFound e) {
                continue;
            } catch (FeignException e) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Driver service temporarily unavailable"
                );
            }
            if (driver == null || driver.status() == null ||
                    !"AVAILABLE".equalsIgnoreCase(driver.status())) {
                continue;
            }

            result.add(NearbyDriverDTO.builder()
                    .driverId(driverId)
                    .driverName(driver.name())
                    .latitude(driverLat)
                    .longitude(driverLon)
                    .distanceKm(distanceKm)
                    .build());
        }

        result.sort(Comparator.comparing(NearbyDriverDTO::getDistanceKm));

        int fromIndex = page * size;

        if (fromIndex >= result.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + size, result.size());
        List<NearbyDriverDTO> pagedResult = result.subList(fromIndex, toIndex);

        redisCacheService.set(cacheKey, pagedResult, Duration.ofMinutes(10));
        return pagedResult;
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

    checkDriverExists(request.getDriverId());

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

    for (Location savedLocation : savedLocations) {
        locationTrackingEventService.recordTrackingEvent(savedLocation);
        invalidateLocationCaches(savedLocation.getId(), savedLocation.getDriverId());
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
            case "eq" -> {
                validateNumeric(value);
                yield locationRepository.findByMetadataEq(key, value);
            }
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
        if (olderThanDays < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "olderThanDays must be >= 0"
            );
        }

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
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F10::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F12::*");

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

    checkDriverExists(driverId);

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

        List<StationaryDriverDTO> result = locationRepository
                .findStationaryDrivers(maxSpeed, cutoffTime)
                .stream()
                .map(p -> {
                    DriverDTO driver;

                    try {
                        driver = driverServiceClient.getDriver(p.getDriverId());
                    } catch (FeignException.NotFound e) {
                        return null;
                    } catch (FeignException e) {
                        throw new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Driver service temporarily unavailable"
                        );
                    }

                    if (driver == null) {
                        return null;
                    }

                    return StationaryDriverDTO.builder()
                            .driverId(p.getDriverId())
                            .driverName(driver.name())
                            .latitude(p.getLatitude())
                            .longitude(p.getLongitude())
                            .lastSpeed(p.getLastSpeed())
                            .lastUpdated(p.getLastUpdated())
                            .build();
                })
                .filter(Objects::nonNull)
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



    // S4-F10 - Location analytics dashboard
// Redis cache TTL: 10 minutes
// Logs ANALYTICS_VIEWED in MongoDB on every invocation, including cache hits.
    public LocationAnalyticsDTO getLocationAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate cannot be after endDate"
            );
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = LocalDateTime.of(
                endDate,
                LocalTime.of(23, 59, 59, 999_000_000)
        );

        String cacheKey = featureKey("S4-F10", startDate, endDate);

        notifyObservers(
                LocationEventActions.ANALYTICS_VIEWED,
                Map.of(
                        "analyticsType", "LOCATION_DASHBOARD",
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString(),
                        "cacheKey", cacheKey
                )
        );


        Object cached = redisCacheService.get(cacheKey);
        LocationAnalyticsDTO cachedDto = toLocationAnalyticsDTO(cached);
        if (cachedDto != null) {
            return cachedDto;
        }


        Long totalLocationEvents =
                locationRepository.countLocationEventsBetween(startDateTime, endDateTime);

        Long activeDrivers =
                locationRepository.countActiveDriversBetween(startDateTime, endDateTime);

        Double averageSpeed =
                locationRepository.findAverageSpeedBetween(startDateTime, endDateTime);

        List<Object[]> hourlyRows =
                locationRepository.countEventsByHourBetween(startDateTime, endDateTime);

        Map<Integer, Long> eventsByHour = new LinkedHashMap<>();

        for (Object[] row : hourlyRows) {
            Integer hour = ((Number) row[0]).intValue();
            Long count = ((Number) row[1]).longValue();
            eventsByHour.put(hour, count);
        }

        LocationAnalyticsDTO dto = LocationAnalyticsDTO.builder()
                .totalLocationEvents(totalLocationEvents != null ? totalLocationEvents : 0L)
                .activeDrivers(activeDrivers != null ? activeDrivers : 0L)
                .averageSpeed(averageSpeed != null ? averageSpeed : 0.0)
                .eventsByHour(eventsByHour)
                .build();

        redisCacheService.set(cacheKey, dto, Duration.ofMinutes(10));

        return dto;
    }



    @SuppressWarnings("unchecked")
    private LocationAnalyticsDTO toLocationAnalyticsDTO(Object cached) {
        if (cached == null) {
            return null;
        }

        if (cached instanceof LocationAnalyticsDTO dto) {
            return dto;
        }

        if (!(cached instanceof Map<?, ?> map)) {
            return null;
        }

        Long totalLocationEvents = toLong(map.get("totalLocationEvents"));
        Long activeDrivers = toLong(map.get("activeDrivers"));
        Double averageSpeed = toDouble(map.get("averageSpeed"));

        Map<Integer, Long> eventsByHour = new LinkedHashMap<>();

        Object rawEventsByHour = map.get("eventsByHour");
        if (rawEventsByHour instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    Integer hour = Integer.parseInt(entry.getKey().toString());
                    Long count = toLong(entry.getValue());
                    eventsByHour.put(hour, count);
                }
            }
        }

        return LocationAnalyticsDTO.builder()
                .totalLocationEvents(totalLocationEvents != null ? totalLocationEvents : 0L)
                .activeDrivers(activeDrivers != null ? activeDrivers : 0L)
                .averageSpeed(averageSpeed != null ? averageSpeed : 0.0)
                .eventsByHour(eventsByHour)
                .build();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(value.toString());
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return Double.parseDouble(value.toString());
    }


    // S4-F11 - Record Location GPS Event
    public void recordLocationGpsEvent(Long driverId, TrackingEventRequest request) {
        checkDriverExists(driverId);

        validateCoordinates(request.getLatitude(), request.getLongitude());

        //Long validRideId = resolveExistingRideIdOrNull(request.getRideId());
        //request.setRideId(validRideId);

        locationTrackingEventService.recordTrackingEvent(driverId, request);

        Map<String, Object> details = new HashMap<>();
        details.put("latitude", request.getLatitude());
        details.put("longitude", request.getLongitude());
        details.put("speed", request.getSpeed());
        details.put("heading", request.getHeading());
        details.put("accuracy", request.getAccuracy());
        details.put("rideId", request.getRideId());
        details.put("notes", request.getNotes());
        details.put("summary", "Tracking event recorded for driver " + driverId);

        notifyObservers(
                LocationEventActions.TRACKING_RECORDED,
                Map.of(
                        "driverId", driverId,
                        "latitude", request.getLatitude(),
                        "longitude", request.getLongitude(),
                        "details", details
                )
        );

        redisCacheService.delete(featureKey("S4-F1", driverId));
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F8::" + driverId + "::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F9::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F10::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F12::" + driverId + "::*");
    }

    /*private Long resolveExistingRideIdOrNull(Long rideId) {
        if (rideId == null) {
            return null;
        }

        return locationRepository.rideExists(rideId) ? rideId : null;
    }*/

    //S4-F12
    public List<LocationTrackingEventDTO> getLocationTrackingTimeline(
            Long driverId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        checkDriverExists(driverId);

        Instant startInstant = startTime != null ? startTime.toInstant(ZoneOffset.UTC) : null;
        Instant endInstant   = endTime   != null ? endTime.toInstant(ZoneOffset.UTC)   : null;

        if (startInstant != null && endInstant != null && startInstant.isAfter(endInstant)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startTime cannot be after endTime"
            );
        }

        String cacheKey = featureKey("S4-F12", driverId, startInstant, endInstant);

        Object cached = redisCacheService.get(cacheKey);
        if (cached instanceof List<?> cachedList) {
            return (List<LocationTrackingEventDTO>) cachedList;
        }

        List<LocationTrackingEventDTO> result =
                locationTrackingEventService.getTrackingEventsForDriver(
                        driverId,
                        startInstant,
                        endInstant
                );

        redisCacheService.set(cacheKey, result, Duration.ofMinutes(5));

        return result;
    }

    //S4 m3 new endpoint
    public Location getRecentLocation(Long driverId) {

        Location location = locationRepository
                .findTopByDriverIdOrderByTimestampDesc(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No recent location found"
                ));

        if (location.getTimestamp() == null ||
                location.getTimestamp().isBefore(LocalDateTime.now().minusMinutes(5))) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No recent location found"
            );
        }

        return location;
    }


    private void checkDriverExists(Long driverId) {
        try {
            driverServiceClient.getDriver(driverId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found: " + driverId);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Driver service temporarily unavailable");
        }
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
            redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F12::" + driverId + "::*");
        }

        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F3::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F5::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F6::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F9::*");
        redisCacheService.deleteByPattern(CACHE_PREFIX + "::S4-F10::*");
    }

   


}
