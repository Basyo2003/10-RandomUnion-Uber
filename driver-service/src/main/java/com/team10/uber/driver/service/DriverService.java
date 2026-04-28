package com.team10.uber.driver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team10.uber.driver.cache.RedisCacheService;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DriverService extends AbstractObservableService {

    // TTLs in seconds — §4.4.1 only
    private static final long TTL_SEARCH = 5  * 60; // F1, F5 — 5 min
    private static final long TTL_DTO    = 10 * 60; // F3, F6, F9 — 10 min

    private final DriverRepository driverRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final MongoEventLogger mongoEventLogger;
    private final RedisCacheService cache;

    public DriverService(DriverRepository driverRepository,
                         DriverDocumentRepository driverDocumentRepository,
                         MongoEventLogger mongoEventLogger,
                         RedisCacheService cache) {
        this.driverRepository = driverRepository;
        this.driverDocumentRepository = driverDocumentRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cache = cache;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    // ── CRUD (no caching — §4.4.2 and §4.4.4 are separate tasks) ────────────

    public Driver createDriver(Driver driver) {
        try {
            Driver saved = driverRepository.save(driver);
            notifyObservers("DRIVER_CREATED", saved);
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

    public Driver getDriverById(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + id));
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
        return saved;
    }

    public void deleteDriver(Long id) {
        Driver driver = getDriverById(id);
        driverRepository.delete(driver);
        notifyObservers("DRIVER_DELETED", id);
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

        List<Driver> result = driverRepository.searchDrivers(status, minRating, maxRating);
        cache.put(key, result, TTL_SEARCH);
        return result;
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
        List<TopRatedDriverDTO> cached = cache.get(key, new TypeReference<List<TopRatedDriverDTO>>() {});
        if (cached != null) return cached;

        List<Object[]> rows = driverRepository.findTopDriversWithRideCount(limit);
        List<TopRatedDriverDTO> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new TopRatedDriverDTO(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    ((Number) row[2]).doubleValue(),
                    ((Number) row[3]).longValue()
            ));
        }
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
}
