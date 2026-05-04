package com.team10.uber.driver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team10.uber.driver.adapter.ElasticsearchHitAdapter;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.dto.DriverDashboardDTO;
import com.team10.uber.driver.dto.DriverDocumentAlertDTO;
import com.team10.uber.driver.dto.DriverEarningsDTO;
import com.team10.uber.driver.dto.TopRatedDriverDTO;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.observer.AbstractObservableService;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.driver.adapter.TopRatedDriverAdapter;
import com.team10.uber.driver.adapter.MongoDocumentAdapter;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DriverService extends AbstractObservableService {

    private static final long TTL_DETAIL = 15 * 60; // §4.4.2 — 15 min
    // TTLs in seconds — §4.4.1 only
    private static final long TTL_SEARCH = 5  * 60; // F1, F5 — 5 min
    private static final long TTL_DTO    = 10 * 60; // F3, F6, F9 — 10 min
    private static final Set<String> ALLOWED_VEHICLE_TYPES =
            Set.of("SEDAN", "SUV", "HATCHBACK", "VAN", "LUXURY");

    private final DriverRepository driverRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final MongoEventLogger mongoEventLogger;
    private final RedisCacheService cache;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchHitAdapter elasticsearchHitAdapter = new ElasticsearchHitAdapter();
        
    // §3.8 — Adapter instances
    private final TopRatedDriverAdapter topRatedDriverAdapter = new TopRatedDriverAdapter();
    private final MongoDocumentAdapter mongoDocumentAdapter = new MongoDocumentAdapter();

    public DriverService(DriverRepository driverRepository,
                         DriverDocumentRepository driverDocumentRepository,
                         MongoEventLogger mongoEventLogger,
                         RedisCacheService cache,
                         ElasticsearchOperations elasticsearchOperations) {
        this.driverRepository = driverRepository;
        this.driverDocumentRepository = driverDocumentRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cache = cache;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    // ── §4.4.4 — Cache invalidation helpers ─────────────────────────────────

    /** Evicts all caches tied to a specific driver. */
    private void evictDriverCaches(Long driverId) {
        cache.evict("driver::" + driverId);
        cache.evictByPattern("S2-F1::*");
        cache.evictByPattern("S2-F10::*");
        cache.evictByPattern("S2-F3::" + driverId + "::*");
        cache.evictByPattern("S2-F5::*");
        cache.evictByPattern("S2-F6::*");
        cache.evictByPattern("S2-F9::*");
    }

    /** Evicts list caches affected when a new driver is created. */
    private void evictDriverListCaches() {
        cache.evictByPattern("S2-F1::*");
        cache.evictByPattern("S2-F10::*");
        cache.evictByPattern("S2-F5::*");
        cache.evictByPattern("S2-F6::*");
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public Driver createDriver(Driver driver) {
        try {
            Driver saved = driverRepository.save(driver);
            notifyObservers("DRIVER_CREATED", saved);
            evictDriverListCaches();
            return saved;
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMessage();
            if (message != null) {
                if (message.toLowerCase().contains("email")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Your driver email is not unique. Add @Column(unique = true) to email and handle DataIntegrityViolationException.");
                } else if (message.toLowerCase().contains("license_number")
                        || message.toLowerCase().contains("licensenumber")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Your licenseNumber is not unique. Add @Column(unique = true) and handle DataIntegrityViolationException.");
                } else if (message.toLowerCase().contains("phone")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Your phone number is not unique.");
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A unique constraint was violated.");
        }
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    /** §4.4.2 — GET /api/drivers/{id}, cached 15 min. */
    public Driver getDriverById(Long id) {
        String key = "driver::" + id;
        Driver cached = cache.get(key, Driver.class);
        if (cached != null) return cached;

        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + id));
        cache.put(key, driver, TTL_DETAIL);
        return driver;
    }

    public Driver updateDriver(Long id, Driver updatedDriver) {
        Driver existingDriver = getDriverById(id);
        existingDriver.setName(updatedDriver.getName());
        existingDriver.setEmail(updatedDriver.getEmail());
        existingDriver.setPhone(updatedDriver.getPhone());
        existingDriver.setLicenseNumber(updatedDriver.getLicenseNumber());
        existingDriver.setStatus(updatedDriver.getStatus());
        existingDriver.setRating(updatedDriver.getRating());
        existingDriver.setTotalRatings(updatedDriver.getTotalRatings());
        existingDriver.setVehicleDetails(updatedDriver.getVehicleDetails());
        existingDriver.setDriverDocuments(updatedDriver.getDriverDocuments());
        Driver saved = driverRepository.save(existingDriver);
        notifyObservers("UPDATED", saved);
        evictDriverCaches(id);
        return saved;
    }

    public void deleteDriver(Long id) {
        Driver driver = getDriverById(id);
        driverRepository.delete(driver);
        notifyObservers("DRIVER_DELETED", id);
        evictDriverCaches(id);
    }

    // ── §4.4.1 — Feature GET endpoints with Redis caching ───────────────────

    /** S2-F1 — search drivers, cached 5 min. */
    public List<Driver> searchDrivers(DriverStatus status, Double minRating, Double maxRating) {
        if (minRating != null && minRating < 0.0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minRating must be >= 0.0");
        if (maxRating != null && maxRating > 5.0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRating must be <= 5.0");
        if (minRating != null && maxRating != null && minRating > maxRating)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rating range: minRating > maxRating");

        String key = "S2-F1::" + status + "::" + minRating + "::" + maxRating;
        List<Driver> cached = cache.get(key, new TypeReference<List<Driver>>() {});
        if (cached != null) return cached;

        String statusStr = (status != null) ? status.name() : null;
        List<Driver> result = driverRepository.searchDrivers(statusStr, minRating, maxRating);
        cache.put(key, result, TTL_SEARCH);
        return result;
    }

    /** S2-F10 — full-text search via Elasticsearch, cached 5 min. */
    public List<Driver> fullTextSearchDrivers(String queryText,
                                              String vehicleType,
                                              DriverStatus status,
                                              Double minRating,
                                              Double maxRating) {
        if (queryText == null || queryText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        if (minRating != null && minRating < 0.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minRating must be >= 0.0");
        }
        if (maxRating != null && maxRating > 5.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRating must be <= 5.0");
        }
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rating range: minRating > maxRating");
        }

        String normalizedVehicleType = null;
        if (vehicleType != null && !vehicleType.isBlank()) {
            normalizedVehicleType = vehicleType.trim().toUpperCase();
            if (!ALLOWED_VEHICLE_TYPES.contains(normalizedVehicleType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "vehicleType must be one of " + Arrays.toString(ALLOWED_VEHICLE_TYPES.toArray()));
            }
        }
        final String finalVehicleType = normalizedVehicleType;

        String normalizedQuery = queryText.trim();
        String key = "S2-F10::" + normalizedQuery.toLowerCase() + "::" + finalVehicleType + "::" + status + "::"
                + minRating + "::" + maxRating;

        List<Driver> cached = cache.get(key, new TypeReference<List<Driver>>() {});
        if (cached != null) {
            notifyObservers("S2_F10_SEARCH_EXECUTED", buildFullTextSearchEventPayload(
                    normalizedQuery, finalVehicleType, status, minRating, maxRating, cached.size(), true));
            return cached;
        }

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.must(m -> m.multiMatch(mm -> mm
                            .query(normalizedQuery)
                            .fields("name", "description")
                            .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BoolPrefix)));
                    if (finalVehicleType != null) {
                        b.filter(f -> f.term(t -> t.field("vehicleType").value(finalVehicleType)));
                    }
                    if (status != null) {
                        b.filter(f -> f.term(t -> t.field("status").value(status.name())));
                    }
                    if (minRating != null || maxRating != null) {
                        b.filter(f -> f.range(r -> r.number(n -> {
                            n.field("rating");
                            if (minRating != null) n.gte(minRating);
                            if (maxRating != null) n.lte(maxRating);
                            return n;
                        })));
                    }
                    return b;
                }))
                .build();

        List<SearchHit<com.team10.uber.driver.model.elastic.DriverSearchDocument>> hits =
                elasticsearchOperations.search(searchQuery,
                        com.team10.uber.driver.model.elastic.DriverSearchDocument.class).getSearchHits();

        if (hits.isEmpty()) {
            cache.put(key, List.of(), TTL_SEARCH);
            notifyObservers("S2_F10_SEARCH_EXECUTED", buildFullTextSearchEventPayload(
                    normalizedQuery, finalVehicleType, status, minRating, maxRating, 0, false));
            return List.of();
        }

        List<Long> orderedIds = hits.stream()
                .map(elasticsearchHitAdapter::adapt)
                .map(doc -> doc.getId())
                .map(id -> {
                    try {
                        return Long.parseLong(id);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<Long, Driver> byId = driverRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(Driver::getId, d -> d, (a, b) -> a, LinkedHashMap::new));

        List<Driver> result = orderedIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        cache.put(key, result, TTL_SEARCH);
        notifyObservers("S2_F10_SEARCH_EXECUTED", buildFullTextSearchEventPayload(
                normalizedQuery, finalVehicleType, status, minRating, maxRating, result.size(), false));
        return result;
    }

    private Map<String, Object> buildFullTextSearchEventPayload(String queryText,
                                                                String vehicleType,
                                                                DriverStatus status,
                                                                Double minRating,
                                                                Double maxRating,
                                                                int resultCount,
                                                                boolean cacheHit) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "S2_F10_SEARCH_EXECUTED");
        payload.put("query", queryText);
        payload.put("vehicleType", vehicleType);
        payload.put("status", status != null ? status.name() : null);
        payload.put("minRating", minRating);
        payload.put("maxRating", maxRating);
        payload.put("resultCount", resultCount);
        payload.put("cacheHit", cacheHit);
        return payload;
    }

    /** S2-F2 — write, no caching. */
    public Driver updateVehicleDetails(Long id, Map<String, Object> vehicleUpdates) {
        Driver existingDriver = getDriverById(id);
        Map<String, Object> merged = new HashMap<>();
        if (existingDriver.getVehicleDetails() != null) merged.putAll(existingDriver.getVehicleDetails());
        if (vehicleUpdates != null) merged.putAll(vehicleUpdates);
        existingDriver.setVehicleDetails(merged);
        Driver saved = driverRepository.save(existingDriver);
        notifyObservers("VEHICLE_DETAILS_UPDATED", saved);
        evictDriverCaches(id);
        return saved;
    }

    /** S2-F3 — earnings summary, cached 10 min. */
    public DriverEarningsDTO getDriverEarningsSummary(Long driverId, LocalDate startDate, LocalDate endDate) {
        Driver driver = getDriverById(driverId);
        if (startDate.isAfter(endDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range: startDate > endDate");

        String key = "S2-F3::" + driverId + "::" + startDate + "::" + endDate;
        DriverEarningsDTO cached = cache.get(key, DriverEarningsDTO.class);
        if (cached != null) return cached;

        LocalDateTime startTs = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        DriverRepository.EarningsAggregate agg = driverRepository.getEarningsAggregate(driverId, startTs, endExclusive);

        long totalRides = (agg == null || agg.getTotalRides() == null) ? 0L : agg.getTotalRides();
        double totalEarnings = (agg == null || agg.getTotalEarnings() == null) ? 0.0 : agg.getTotalEarnings();
        double averageFare = totalRides == 0L ? 0.0 : totalEarnings / totalRides;

        DriverEarningsDTO result = new DriverEarningsDTO(driver.getId(), driver.getName(), totalRides, totalEarnings, averageFare);
        cache.put(key, result, TTL_DTO);
        return result;
    }

    /** S2-F4 — write, no caching. */
    @Transactional
    public void updateDriverAvailability(Long driverId, DriverStatus newStatus) {
        Driver driver = getDriverById(driverId);
        if (newStatus == DriverStatus.OFFLINE) {
            int activeRides = driverRepository.countActiveRides(driverId);
            if (activeRides > 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Driver has active rides and cannot go OFFLINE");
        }
        driver.setStatus(newStatus);
        Driver saved = driverRepository.save(driver);
        notifyObservers("AVAILABILITY_UPDATED", saved);
        evictDriverCaches(driverId);
    }

    /** S2-F5 — filter by vehicle type, cached 5 min. */
    public List<Driver> filterByVehicleType(String type, String status) {
        if (type == null || type.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle type is required");

        DriverStatus driverStatus = null;
        if (status != null) {
            try {
                driverStatus = DriverStatus.valueOf(status);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value");
            }
        }
        String statusValue = (driverStatus != null) ? driverStatus.name() : null;

        String key = "S2-F5::" + type + "::" + statusValue;
        List<Driver> cached = cache.get(key, new TypeReference<List<Driver>>() {});
        if (cached != null) return cached;

        List<Driver> result = driverRepository.findByVehicleType(type, statusValue);
        cache.put(key, result, TTL_SEARCH);
        return result;
    }

    /** S2-F6 — top rated drivers, cached 10 min. */
    public List<TopRatedDriverDTO> getTopRatedDrivers(int limit) {
        String key = "S2-F6::" + limit;
        List<TopRatedDriverDTO> cached = cache.get(key,
                new TypeReference<List<TopRatedDriverDTO>>() {});
        if (cached != null) return cached;

        List<Object[]> rows = driverRepository.findTopDriversWithRideCount(limit);

        // §3.8 — Use Adapter instead of inline Object[] mapping
        List<TopRatedDriverDTO> result = topRatedDriverAdapter.adaptAll(rows);
        cache.put(key, result, TTL_DTO);
        return result;
    }
    /** S2-F7 — write, no caching. */
    @Transactional
    public Driver rateDriver(Long id, Long rideId, Double rating) {
        Driver driver = getDriverById(id);

        Driver rideDriver = driverRepository.findDriverByRideId(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No driver assigned to ride with id: " + rideId));

        String rideStatus = driverRepository.findRideStatusById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ride not found with id: " + rideId));

        if (!driver.getId().equals(rideDriver.getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride does not belong to the requested driver");

        if (!rideStatus.equals("COMPLETED"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot rate driver for a ride that is not completed");

        if (rating == null || rating < 1.0 || rating > 5.0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1.0 and 5.0");

        double totalRatingScore = (driver.getRating() * driver.getTotalRatings()) + rating;
        int updatedTotalRatings = driver.getTotalRatings() + 1;
        driver.setTotalRatings(updatedTotalRatings);
        driver.setRating(totalRatingScore / updatedTotalRatings);

        Driver saved = driverRepository.save(driver);
        notifyObservers("RATING_RECORDED", saved);
        evictDriverCaches(id);
        return saved;
    }

    /** S2-F9 — drivers with expired documents, cached 10 min. */
    public List<DriverDocumentAlertDTO> getDriversWithExpiredDocuments() {
        String key = "S2-F9::all";
        List<DriverDocumentAlertDTO> cached = cache.get(key, new TypeReference<List<DriverDocumentAlertDTO>>() {});
        if (cached != null) return cached;

        List<DriverDocument> expiredDocuments = driverDocumentRepository.findExpiredDocuments();
        Map<Long, List<DriverDocument>> groupedByDriver = new HashMap<>();
        for (DriverDocument doc : expiredDocuments) {
            Long dId = doc.getDriver().getId();
            groupedByDriver.computeIfAbsent(dId, k -> new ArrayList<>()).add(doc);
        }

        List<DriverDocumentAlertDTO> result = new ArrayList<>();
        for (Long dId : groupedByDriver.keySet()) {
            Driver driver = getDriverById(dId);
            result.add(new DriverDocumentAlertDTO(driver, groupedByDriver.get(dId)));
        }

        cache.put(key, result, TTL_DTO);
        return result;
    }

    /** S2-F12 — Driver Performance Dashboard, cached 10 min.
     *  MongoDB DASHBOARD_VIEWED fires on every invocation (cache hit or miss). */
    public DriverDashboardDTO getDriverDashboard(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + driverId));

        // Log every invocation — outside cache
        notifyObservers("DASHBOARD_VIEWED", driver);

        String key = "S2-F12::" + driverId;
        DriverDashboardDTO cached = cache.get(key, DriverDashboardDTO.class);
        if (cached != null) return cached;

        DriverRepository.DashboardAggregate agg = driverRepository.getDashboardAggregate(driverId);

        long totalRides      = (agg == null || agg.getTotalRides() == null)      ? 0L  : agg.getTotalRides();
        double totalEarnings = (agg == null || agg.getTotalEarnings() == null)   ? 0.0 : agg.getTotalEarnings();
        double avgRideFare   = (agg == null || agg.getAverageRideFare() == null) ? 0.0 : agg.getAverageRideFare();

        DriverDashboardDTO result = DriverDashboardDTO.builder()
                .driverId(driver.getId())
                .name(driver.getName())
                .totalRides(totalRides)
                .totalEarnings(totalEarnings)
                .averageRideFare(avgRideFare)
                .averageRating(driver.getRating())
                .totalRatings(driver.getTotalRatings())
                .build();

        cache.put(key, result, TTL_DTO);
        return result;
    }

    /** S2-F11 — reindex driver to Elasticsearch (delegation to DriverIndexService at controller layer) */
    public void indexDriver(Long id) {
        driverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Driver not found: " + id));
        notifyObservers("DRIVER_INDEXED", id);
    }

    /** S2-F8 — verify a driver document */
    public DriverDocument verifyDocument(Long driverId, Long documentId) {
        driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Driver not found: " + driverId));
        return driverDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document not found: " + documentId));
    }
}
