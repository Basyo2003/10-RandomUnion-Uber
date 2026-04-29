package com.team10.uber.driver.service;

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

    private static final long TTL_DETAIL = 15 * 60; // §4.4.2 — 15 min

    private final DriverRepository driverRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final MongoEventLogger mongoEventLogger;
    private final RedisCacheService cache;

    public DriverService(DriverRepository driverRepository, DriverDocumentRepository driverDocumentRepository,
                         MongoEventLogger mongoEventLogger, RedisCacheService cache) {
        this.driverRepository = driverRepository;
        this.driverDocumentRepository = driverDocumentRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cache = cache;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

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

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A unique constraint was violated.");
        }
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public List<Driver> searchDrivers(DriverStatus status, Double minRating, Double maxRating) {
        if (minRating != null && minRating < 0.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minRating must be >= 0.0");
        }
        if (maxRating != null && maxRating > 5.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRating must be <= 5.0");
        }
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rating range: minRating > maxRating");
        }
        return driverRepository.searchDrivers(status, minRating, maxRating);
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
        return saved;
    }

    public Driver updateVehicleDetails(Long id, Map<String, Object> vehicleUpdates) {
        Driver existingDriver = getDriverById(id); // 404 if not found

        Map<String, Object> merged = new HashMap<>();
        if (existingDriver.getVehicleDetails() != null) {
            merged.putAll(existingDriver.getVehicleDetails());
        }
        if (vehicleUpdates != null) {
            merged.putAll(vehicleUpdates);
        }

        existingDriver.setVehicleDetails(merged);
        Driver saved = driverRepository.save(existingDriver);
        notifyObservers("VEHICLE_DETAILS_UPDATED", saved);
        return saved;
    }

    public List<Driver> filterByVehicleType(String type, String status) {

        // 🔴 Validate type
        if (type == null || type.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle type is required");
        }

        // 🔴 Convert status (if exists)
        DriverStatus driverStatus = null;

        if (status != null) {
            try {
                driverStatus = DriverStatus.valueOf(status);
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid status value");
            }
        }

        // 🔴 Convert enum to string for query
        String statusValue = (driverStatus != null) ? driverStatus.name() : null;

        return driverRepository.findByVehicleType(type, statusValue);
    }

    public DriverEarningsDTO getDriverEarningsSummary(Long driverId, LocalDate startDate, LocalDate endDate) {
        Driver driver = getDriverById(driverId); // 404 if not found

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range: startDate > endDate");
        }

        LocalDateTime startTs = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        DriverRepository.EarningsAggregate agg = driverRepository.getEarningsAggregate(driverId, startTs, endExclusive);

        long totalRides = (agg == null || agg.getTotalRides() == null) ? 0L : agg.getTotalRides();
        double totalEarnings = (agg == null || agg.getTotalEarnings() == null) ? 0.0 : agg.getTotalEarnings();
        double averageFare = totalRides == 0L ? 0.0 : totalEarnings / totalRides;

        return new DriverEarningsDTO(driver.getId(), driver.getName(), totalRides, totalEarnings, averageFare);
    }

    public void deleteDriver(Long id) {
        Driver driver = getDriverById(id);
        driverRepository.delete(driver);
        notifyObservers("DRIVER_DELETED", id);
    }

    public List<TopRatedDriverDTO> getTopRatedDrivers(int limit) {

        List<Object[]> rows = driverRepository.findTopDriversWithRideCount(limit);

        List<TopRatedDriverDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            result.add(new TopRatedDriverDTO(
                    ((Number) row[0]).longValue(), // driverId
                    (String) row[1], // name
                    ((Number) row[2]).doubleValue(), // rating
                    ((Number) row[3]).longValue() // totalRides
            ));
        }

        return result;
    }

    @Transactional
    public Driver rateDriver(Long id, Long rideId, Double rating) {
        Driver driver = getDriverById(id);

        Driver rideDriver = driverRepository.findDriverByRideId(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No driver assigned to ride with id: " + rideId));

        String rideStatus = driverRepository.findRideStatusById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ride not found with id: " + rideId));

        if (!driver.getId().equals(rideDriver.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride does not belong to the requested driver");
        }

        if (!rideStatus.equals("COMPLETED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot rate driver for a ride that is not completed");
        }

        if (rating == null || rating < 1.0 || rating > 5.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rating must be between 1.0 and 5.0");
        }

        double totalRatingScore = (driver.getRating() * driver.getTotalRatings()) + rating;
        int updatedTotalRatings = driver.getTotalRatings() + 1;

        driver.setTotalRatings(updatedTotalRatings);
        driver.setRating(totalRatingScore / updatedTotalRatings);

        Driver saved = driverRepository.save(driver);
        notifyObservers("RATING_RECORDED", saved);
        return saved;
    }

    @Transactional
    public void updateDriverAvailability(Long driverId, DriverStatus newStatus) {

        Driver driver = getDriverById(driverId); // already throws 404

        // 🔴 IMPORTANT CHECK
        if (newStatus == DriverStatus.OFFLINE) {
            int activeRides = driverRepository.countActiveRides(driverId);

            if (activeRides > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Driver has active rides and cannot go OFFLINE");
            }
        }

        driver.setStatus(newStatus);
        Driver saved = driverRepository.save(driver);
        notifyObservers("AVAILABILITY_UPDATED", saved);
    }

    public List<DriverDocumentAlertDTO> getDriversWithExpiredDocuments() {
        List<DriverDocument> expiredDocuments = driverDocumentRepository.findExpiredDocuments();

        // Group expired documents by driver ID
        Map<Long, List<DriverDocument>> groupedByDriver = new HashMap<>();
        for (DriverDocument doc : expiredDocuments) {
            Long driverId = doc.getDriver().getId();
            groupedByDriver.computeIfAbsent(driverId, k -> new ArrayList<>()).add(doc);
        }

        // Build DTOs for each driver with expired documents
        List<DriverDocumentAlertDTO> result = new ArrayList<>();
        for (Long driverId : groupedByDriver.keySet()) {
            Driver driver = getDriverById(driverId);
            List<DriverDocument> docsForDriver = groupedByDriver.get(driverId);
            result.add(new DriverDocumentAlertDTO(driver, docsForDriver));
        }

        return result;
    }
}
