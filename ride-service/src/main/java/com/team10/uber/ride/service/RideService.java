package com.team10.uber.ride.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import com.team10.uber.contracts.dto.DriverAvailabilityDTO;
import com.team10.uber.contracts.dto.DriverDTO;
import com.team10.uber.contracts.dto.LocationDTO;
import com.team10.uber.contracts.dto.UserDTO;
import com.team10.uber.contracts.events.RideCancelledEvent;
import com.team10.uber.contracts.events.RideCompletedEvent;
import com.team10.uber.contracts.events.RidePlacedEvent;
import com.team10.uber.contracts.dto.DriverRideSummaryDTO;
import com.team10.uber.contracts.dto.RideSummaryDTO;
import com.team10.uber.contracts.feign.DriverServiceClient;
import com.team10.uber.contracts.feign.LocationServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.contracts.dto.RideDTO;

import com.team10.uber.ride.dto.*;
import com.team10.uber.ride.exception.ServiceUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.team10.uber.ride.dto.FareEstimateDTO;
import com.team10.uber.ride.dto.FareEstimateRequestDTO;
import com.team10.uber.ride.dto.RideAnalyticsDTO;
import com.team10.uber.ride.dto.RideDetailsDTO;
import com.team10.uber.ride.dto.RideStopRequestDTO;
import com.team10.uber.ride.dto.RideWithStopsDTO;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.enums.StopStatus;
import com.team10.uber.ride.exception.DriverNotFoundException;
import com.team10.uber.ride.exception.InvalidRideStatusException;
import com.team10.uber.ride.exception.RideNotFoundException;
import com.team10.uber.ride.messaging.publishers.RideEventPublisher;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.model.RideStop;
import com.team10.uber.ride.observer.AbstractObservableService;
import com.team10.uber.ride.observer.MongoEventLogger;
import com.team10.uber.ride.repository.RideRepository;
import com.team10.uber.ride.repository.RideStopRepository;
import feign.FeignException;
import jakarta.annotation.PostConstruct;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RideService extends AbstractObservableService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final RideStopRepository rideStopRepository;
    private final MongoEventLogger mongoEventLogger;
    private final CacheInvalidationService cacheInvalidation;
    private final Driver neo4jDriver;
    
    private final DriverServiceClient driverServiceClient;
    private final UserServiceClient userServiceClient;
    private final LocationServiceClient locationServiceClient;
    private final RideEventPublisher rideEventPublisher;

    public RideService(RideRepository rideRepository,
                       RideStopRepository rideStopRepository,
                       MongoEventLogger mongoEventLogger,
                       CacheInvalidationService cacheInvalidation,
                       Driver neo4jDriver,
                       DriverServiceClient driverServiceClient,
                       UserServiceClient userServiceClient,
                       LocationServiceClient locationServiceClient,
                       RideEventPublisher rideEventPublisher) {
        this.rideRepository = rideRepository;
        this.rideStopRepository = rideStopRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cacheInvalidation = cacheInvalidation;
        this.neo4jDriver = neo4jDriver;
        this.driverServiceClient = driverServiceClient;
        this.userServiceClient = userServiceClient;
        this.locationServiceClient = locationServiceClient;
        this.rideEventPublisher = rideEventPublisher;
    }

    private void evictRideFeatureCaches() {
        cacheInvalidation.evictByPattern("ride-service::S3-F1::*");
        cacheInvalidation.evictByPattern("ride-service::S3-F3::*");
        cacheInvalidation.evictByPattern("ride-service::S3-F5::*");
        cacheInvalidation.evictByPattern("ride-service::S3-F6::*");
        cacheInvalidation.evictByPattern("ride-service::S3-F10::*");
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    public Ride createRide(Ride ride) {
        if (ride.getStatus() == null) {
            ride.setStatus(RideStatus.REQUESTED);
        }
        log.info("DB_WRITE ride.create userId={}", ride.getUserId());
        Ride saved = rideRepository.save(ride);
        MDC.put("rideId", String.valueOf(saved.getId()));
        MDC.put("userId", String.valueOf(saved.getUserId()));
        log.info("DB_WRITE ride.created rideId={} userId={} status={}", saved.getId(), saved.getUserId(), saved.getStatus());
        MDC.remove("rideId");
        MDC.remove("userId");
        notifyObservers("RIDE_CREATED", saved);
        evictRideFeatureCaches();
        return saved;
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    @Cacheable(value = "ride-service::ride", key = "#id")
    public Ride getRideById(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + id));
    }

    public RideDTO getRideDto(Long id) {
        Ride ride = getRideById(id);
        return new RideDTO(
                ride.getId(),
                ride.getUserId(),
                ride.getDriverId(),
                ride.getStatus() != null ? ride.getStatus().name() : null,
                ride.getFare(),
                ride.getRequestedAt(),
                ride.getCompletedAt(),
                ride.getMetadata()
        );
    }

    @CacheEvict(value = "ride-service::ride", key = "#id")
    public Ride updateRide(Long id, Ride updatedRide) {
        Ride existing = getRideById(id);
        existing.setUserId(updatedRide.getUserId());
        existing.setDriverId(updatedRide.getDriverId());
        existing.setPickupLatitude(updatedRide.getPickupLatitude());
        existing.setPickupLongitude(updatedRide.getPickupLongitude());
        existing.setDropoffLatitude(updatedRide.getDropoffLatitude());
        existing.setDropoffLongitude(updatedRide.getDropoffLongitude());
        existing.setStatus(updatedRide.getStatus());
        existing.setFare(updatedRide.getFare());
        existing.setMetadata(updatedRide.getMetadata());
        Ride saved = rideRepository.save(existing);
        notifyObservers("RIDE_UPDATED", saved);
        cacheInvalidation.evictByPattern("ride-service::S3-F9::" + id);
        evictRideFeatureCaches();
        return saved;
    }

    @CacheEvict(value = "ride-service::ride", key = "#id")
    public void deleteRide(Long id) {
        Ride ride = getRideById(id);
        rideRepository.delete(ride);
        notifyObservers("RIDE_DELETED", id);
        cacheInvalidation.evictByPattern("ride-service::S3-F9::" + id);
        evictRideFeatureCaches();
    }

    // §8.4 S3-F7 — remove direct driver write; publish ride.cancelled instead
    @Transactional
    public Ride cancelRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId));

        RideStatus currentStatus = ride.getStatus();
        if (currentStatus != RideStatus.REQUESTED && currentStatus != RideStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride can only be cancelled when status is REQUESTED or ACCEPTED. Current status: "
                            + currentStatus);
        }

        log.info("Ride {} transitioning {} → {}", rideId, currentStatus, RideStatus.CANCELLED);
        ride.setStatus(RideStatus.CANCELLED);
        Ride saved = rideRepository.save(ride);

        // driver-service and payment-service each consume ride.cancelled and update their own DBs
        rideEventPublisher.publishRideCancelled(
                new RideCancelledEvent(saved.getId(), saved.getUserId(), saved.getDriverId(), "user_requested")
        );

        notifyObservers("RIDE_CANCELLED", saved);
        cacheInvalidation.evictByPattern("ride-service::ride::" + rideId);
        cacheInvalidation.evictByPattern("ride-service::S3-F9::" + rideId);
        evictRideFeatureCaches();
        return saved;
    }

    // §8.3 S3-F4 — 3 Feign pre-checks; no direct driver/payment writes; publish ride.completed
    @Transactional
    public Ride completeRide(Long rideId) {
        Ride ride = getRideById(rideId);

        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new InvalidRideStatusException(
                    "Ride status must be IN_PROGRESS to complete. Current status: " + ride.getStatus()
            );
        }

        if (ride.getFare() == null) {
            ride.setFare(calculateFare(
                    ride.getPickupLatitude(), ride.getPickupLongitude(),
                    ride.getDropoffLatitude(), ride.getDropoffLongitude()
            ));
        }

        // Pre-check 1: user must be ACTIVE
        try {
            UserDTO user = userServiceClient.getUser(ride.getUserId());
            if (!"ACTIVE".equals(user.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not active");
            }
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user-service temporarily unavailable");
        }

        // Pre-check 2: driver must be BUSY
        try {
            DriverDTO driver = driverServiceClient.getDriver(ride.getDriverId());
            if (!"BUSY".equals(driver.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver is not BUSY");
            }
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver not found");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "driver-service temporarily unavailable");
        }

        // Pre-check 3: location ping must be within the last 5 minutes
        try {
            LocationDTO location = locationServiceClient.getRecentLocationForDriver(ride.getDriverId());
            if (location.timestamp().isBefore(LocalDateTime.now().minusMinutes(5))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "driver not actively tracked");
            }
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "driver not actively tracked");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "location-service temporarily unavailable");
        }

        log.info("Ride {} transitioning {} → {}", rideId, RideStatus.IN_PROGRESS, RideStatus.COMPLETED);
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());
        Ride saved = rideRepository.save(ride);

        rideEventPublisher.publishRideCompleted(
                new RideCompletedEvent(saved.getId(), saved.getUserId(), saved.getDriverId(), saved.getFare())
        );

        notifyObservers("RIDE_COMPLETED", saved);
        cacheInvalidation.evictByPattern("ride-service::ride::" + rideId);
        cacheInvalidation.evictByPattern("ride-service::S3-F9::" + rideId);
        evictRideFeatureCaches();
        return saved;
    }

    private double calculateFare(double pickupLat, double pickupLon, double dropoffLat, double dropoffLon) {
        double distance = Math.sqrt(
                Math.pow(dropoffLat - pickupLat, 2) +
                        Math.pow(dropoffLon - pickupLon, 2)
        ) * 111;
        return distance * 15.0;
    }

    // §5 S3-F2 — replace cross-service SQL with Feign; publish ride.placed instead of direct driver write
    @Transactional
    public Ride assignDriver(Long rideId, Long driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ride not found with id: " + rideId));

        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new InvalidRideStatusException(
                    "Ride " + rideId + " cannot be assigned. Expected REQUESTED but was: " + ride.getStatus()
            );
        }

        // §2.4 — Feign: driver must exist and be AVAILABLE
        try {
            DriverAvailabilityDTO availability = driverServiceClient.getDriverAvailability(driverId);
            if (!"AVAILABLE".equals(availability.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver is not available");
            }
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found with id: " + driverId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "driver-service temporarily unavailable");
        }

        log.info("Ride {} transitioning {} → {}", rideId, RideStatus.REQUESTED, RideStatus.ACCEPTED);
        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.ACCEPTED);
        Ride saved = rideRepository.save(ride);

        // driver-service consumer of ride.placed flips driver status to BUSY in its own DB
        rideEventPublisher.publishRidePlaced(new RidePlacedEvent(saved.getId(), saved.getUserId(), driverId));

        notifyObservers("DRIVER_ASSIGNED", saved);
        cacheInvalidation.evictByPattern("ride-service::ride::" + rideId);
        cacheInvalidation.evictByPattern("ride-service::S3-F9::" + rideId);
        cacheInvalidation.evictByPattern("driver-service::S2-F12::" + driverId);
        evictRideFeatureCaches();
        return saved;
    }

    public RideWithStopsDTO addStops(Long rideId, List<RideStopRequestDTO> stopRequests) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot add stops to a completed or cancelled ride");
        }

        for (RideStopRequestDTO req : stopRequests) {
            if (req.getLatitude() == null || req.getLongitude() == null || req.getAddress() == null || req.getAddress().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each stop must have latitude, longitude, and address");
            }
        }

        int maxOrder = rideStopRepository.findMaxStopOrderByRideId(rideId);

        List<RideStop> newStops = new ArrayList<>();
        for (RideStopRequestDTO req : stopRequests) {
            RideStop stop = new RideStop();
            stop.setLatitude(req.getLatitude());
            stop.setLongitude(req.getLongitude());
            stop.setAddress(req.getAddress());
            stop.setMetadata(req.getMetadata());
            stop.setStatus(StopStatus.PENDING);
            stop.setStopOrder(++maxOrder);
            stop.setRide(ride);
            newStops.add(stop);
        }

        rideStopRepository.saveAll(newStops);

        List<RideStop> allStops = rideStopRepository.findByRideIdOrderByStopOrderAsc(rideId);
        notifyObservers("STOPS_ADDED", ride);
        cacheInvalidation.evictByPattern("ride-service::ride::" + rideId);
        cacheInvalidation.evictByPattern("ride-service::S3-F9::" + rideId);
        evictRideFeatureCaches();
        return new RideWithStopsDTO(ride, allStops);
    }

    @Cacheable(value = "ride-service::S3-F5", key = "#key + '_' + #value")
    public List<Ride> filterByMetadata(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key and value must not be blank");
        }
        return rideRepository.findByMetadataField(key, value);
    }

    @Cacheable(value = "ride-service::S3-F3", key = "#request.pickupLatitude + '_' + #request.pickupLongitude + '_' + #request.dropoffLatitude + '_' + #request.dropoffLongitude")
    public FareEstimateDTO getFareEstimate(FareEstimateRequestDTO request) {
        if (request.getPickupLatitude() == null || request.getPickupLongitude() == null
                || request.getDropoffLatitude() == null || request.getDropoffLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "pickupLatitude, pickupLongitude, dropoffLatitude, and dropoffLongitude are required");
        }
        double distance = Math.sqrt(
                Math.pow(request.getDropoffLatitude() - request.getPickupLatitude(), 2) +
                        Math.pow(request.getDropoffLongitude() - request.getPickupLongitude(), 2)
        ) * 111;

        double duration = (distance / 40) * 60;

        int nearbyCount = rideRepository.countActiveRidesNearPickup(
                request.getPickupLatitude() - 0.01, request.getPickupLatitude() + 0.01,
                request.getPickupLongitude() - 0.01, request.getPickupLongitude() + 0.01
        );

        double surgeMultiplier;
        if (nearbyCount <= 10) {
            surgeMultiplier = 1.0;
        } else if (nearbyCount <= 20) {
            surgeMultiplier = 1.5;
        } else {
            surgeMultiplier = 2.0;
        }

        double fare = 15.0 * distance * surgeMultiplier;
        return new FareEstimateDTO(distance, duration, fare, surgeMultiplier);
    }

    @Cacheable(value = "ride-service::S3-F6", key = "#startDate + '_' + #endDate")
    public RideAnalyticsDTO getRideAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Ride> rides = rideRepository.findByRequestedAtBetween(startDateTime, endDateTime);

        long totalRides = rides.size();
        long completedRides = rides.stream()
                .filter(ride -> ride.getStatus() == RideStatus.COMPLETED)
                .count();
        long cancelledRides = rides.stream()
                .filter(ride -> ride.getStatus() == RideStatus.CANCELLED)
                .count();

        double totalRevenue = rides.stream()
                .filter(ride -> ride.getStatus() == RideStatus.COMPLETED)
                .map(Ride::getFare)
                .filter(fare -> fare != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        double averageFare = completedRides > 0 ? totalRevenue / completedRides : 0.0;
        double completionRate = totalRides > 0 ? (double) completedRides / totalRides : 0.0;

        return new RideAnalyticsDTO(totalRides, completedRides, cancelledRides, totalRevenue, averageFare, completionRate);
    }

    @Cacheable(value = "ride-service::S3-F1", key = "(#status != null ? #status.name() : 'ALL') + '_' + #startDate + '_' + #endDate")
    public List<Ride> getRidesByStatusAndDateRange(RideStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        String statusName = status != null ? status.name() : null;
        return rideRepository.getRidesByStatusAndDateRange(startDate, endDate, statusName);
    }

    public List<Ride> getRidesByStatusAndDateRange(RideStatus status, LocalDateTime startDate, LocalDateTime endDate, Long userId) {
        String statusName = status != null ? status.name() : null;
        return rideRepository.searchRides(startDate, endDate, statusName, userId);
    }

    @Cacheable(value = "ride-service::S3-F9", key = "#rideId")
    public RideDetailsDTO GetRideDetailsWithStops(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId));

        List<RideStop> stops = rideStopRepository.findByRideIdOrderByStopOrderAsc(rideId);
        return new RideDetailsDTO(ride, stops);
    }

    public void validateDashboardDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be on or before endDate");
        }
    }

    @Cacheable(value = "ride-service::S3-F10", key = "#startDate + '_' + #endDate")
    public RideAnalyticsDashboardDTO getRideAnalyticsDashboard(LocalDate startDate, LocalDate endDate) {
        validateDashboardDateRange(startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        Map<String, Long> ridesByStatus = new LinkedHashMap<>();
        long totalRides = 0L;
        long completedRides = 0L;
        List<Object[]> rows = rideRepository.countRidesByStatusBetween(startDateTime, endDateTime);
        for (Object[] row : rows) {
            String statusName = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            ridesByStatus.put(statusName, count);
            totalRides += count;
            if (RideStatus.COMPLETED.name().equals(statusName)) {
                completedRides = count;
            }
        }

        Double revenueSum = rideRepository.sumCompletedRideRevenueBetween(startDateTime, endDateTime);
        double totalRevenue = revenueSum != null ? revenueSum : 0.0;
        double averageRideFare = completedRides > 0 ? totalRevenue / completedRides : 0.0;
        double completionRate = totalRides > 0 ? (double) completedRides / totalRides : 0.0;

        return RideAnalyticsDashboardDTO.builder()
                .totalRides(totalRides)
                .totalRevenue(totalRevenue)
                .averageRideFare(averageRideFare)
                .completionRate(completionRate)
                .ridesByStatus(ridesByStatus)
                .build();
    }

    @Async
    public void logAnalyticsViewed(LocalDate startDate, LocalDate endDate, Long userId) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("action", "ANALYTICS_VIEWED");
            details.put("endpoint", "/api/rides/analytics/dashboard");
            details.put("startDate", startDate != null ? startDate.toString() : null);
            details.put("endDate", endDate != null ? endDate.toString() : null);
            if (userId != null) details.put("userId", userId);
            notifyObservers("ANALYTICS_VIEWED", details);
        } catch (Exception ignored) {
        }
    }

    // §5 S3-F11 — replace cross-service SQL with Feign; use returned names for Neo4j node properties
    public String recordInteraction(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new InvalidRideStatusException(
                    "Ride must be COMPLETED to record interaction. Current status: " + ride.getStatus());
        }

        Long userId = ride.getUserId();
        Long driverId = ride.getDriverId();

        UserDTO user;
        try {
            user = userServiceClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user-service temporarily unavailable");
        }

        DriverDTO driver;
        try {
            driver = driverServiceClient.getDriver(driverId);
        } catch (FeignException.NotFound e) {
            throw new DriverNotFoundException("Driver not found with id: " + driverId);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "driver-service temporarily unavailable");
        }

        String userName = user.name() != null ? user.name() : "";
        String driverName = driver.name() != null ? driver.name() : "";
        String vehicleType = driver.vehicleDetails() != null
                ? (String) driver.vehicleDetails().getOrDefault("vehicleType", "") : "";

        try (Session session = neo4jDriver.session()) {
            boolean alreadyRecorded = session.executeRead(tx -> {
                var result = tx.run(
                        "MATCH (u:User {userId: $userId})-[r:RODE_WITH]->(d:Driver {driverId: $driverId}) " +
                        "WHERE $rideId IN r.recordedRideIds RETURN count(r) > 0 AS exists",
                        Values.parameters("userId", userId, "driverId", driverId, "rideId", rideId)
                );
                return result.hasNext() && result.next().get("exists").asBoolean(false);
            });

            if (alreadyRecorded) {
                return "Interaction already recorded for ride " + rideId;
            }

            String uName = userName;
            String dName = driverName;
            String vType = vehicleType;

            session.executeWrite(tx -> {
                tx.run(
                        "MERGE (u:User {userId: $userId}) ON CREATE SET u.name = $userName " +
                        "MERGE (d:Driver {driverId: $driverId}) ON CREATE SET d.name = $driverName, d.vehicleType = $vehicleType " +
                        "MERGE (u)-[r:RODE_WITH]->(d) " +
                        "ON CREATE SET r.rideCount = 1, r.lastRideDate = $now, r.recordedRideIds = [$rideId] " +
                        "ON MATCH SET r.rideCount = r.rideCount + 1, r.lastRideDate = $now, " +
                        "            r.recordedRideIds = r.recordedRideIds + [$rideId]",
                        Values.parameters(
                                "userId", userId, "userName", uName,
                                "driverId", driverId, "driverName", dName, "vehicleType", vType,
                                "rideId", rideId, "now", LocalDateTime.now().toString()
                        )
                );
                return null;
            });
        }

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put("rideId", rideId);
        eventDetails.put("userId", userId);
        eventDetails.put("driverId", driverId);
        notifyObservers("INTERACTION_RECORDED", eventDetails);
        cacheInvalidation.evictByPattern("ride-service::S3-F12::*");

        return "Interaction recorded successfully for ride " + rideId;
    }

    // --- §5 Feign-target summary/count endpoints ---

    public RideSummaryDTO getUserRideSummary(Long userId) {
        log.info("FEIGN_CALL user-service.getUser userId={} start", userId);
        try {
            userServiceClient.getUser(userId);
            log.info("FEIGN_CALL user-service.getUser userId={} success", userId);
        } catch (FeignException.NotFound e) {
            log.warn("FEIGN_CALL user-service.getUser userId={} not-found", userId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
        } catch (FeignException e) {
            log.error("FEIGN_CALL user-service.getUser userId={} error: {}", userId, e.getMessage());
            throw new ServiceUnavailableException("User service unavailable", e);
        }

        long totalRides = rideRepository.countTotalRidesByUserId(userId);
        long completedRides = rideRepository.countCompletedRidesByUserId(userId);
        long cancelledRides = rideRepository.countCancelledRidesByUserId(userId);
        Double totalSpent = rideRepository.sumFareByUserId(userId);
        double spent = totalSpent != null ? totalSpent : 0.0;
        double avgFare = completedRides > 0 ? spent / completedRides : 0.0;
        return new RideSummaryDTO(totalRides, completedRides, cancelledRides, spent, avgFare);
    }

    public int getUserActiveRideCount(Long userId) {
        return rideRepository.countActiveRidesByUserId(userId);
    }

    public long getUserCompletedRideCount(Long userId) {
        return rideRepository.countCompletedRidesByUserId(userId);
    }

    public DriverRideSummaryDTO getDriverRideSummary(Long driverId, String startDate, String endDate) {
        log.info("FEIGN_CALL driver-service.getDriver driverId={} start", driverId);
        try {
            driverServiceClient.getDriver(driverId);
            log.info("FEIGN_CALL driver-service.getDriver driverId={} success", driverId);
        } catch (FeignException.NotFound e) {
            log.warn("FEIGN_CALL driver-service.getDriver driverId={} not-found", driverId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found with id: " + driverId);
        } catch (FeignException e) {
            log.error("FEIGN_CALL driver-service.getDriver driverId={} error: {}", driverId, e.getMessage());
            throw new ServiceUnavailableException("Driver service unavailable", e);
        }

        LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = endDate != null ? LocalDate.parse(endDate).atTime(LocalTime.MAX) : null;

        long totalRides = rideRepository.countCompletedRidesByDriverIdInRange(driverId, start, end);
        Double totalEarnings = rideRepository.sumFareByDriverIdInRange(driverId, start, end);
        double earnings = totalEarnings != null ? totalEarnings : 0.0;
        double avgFare = totalRides > 0 ? earnings / totalRides : 0.0;
        return new DriverRideSummaryDTO(totalRides, earnings, avgFare);
    }

    public int getDriverActiveRideCount(Long driverId) {
        return rideRepository.countActiveRidesByDriverId(driverId);
    }

    public long getDriverCompletedRideCount(Long driverId) {
        return rideRepository.countCompletedRidesByDriverId(driverId);
    }
}
