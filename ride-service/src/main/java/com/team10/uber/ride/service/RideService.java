package com.team10.uber.ride.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.model.RideStop;
import com.team10.uber.ride.observer.AbstractObservableService;
import com.team10.uber.ride.observer.MongoEventLogger;
import com.team10.uber.ride.repository.RideRepository;
import com.team10.uber.ride.repository.RideStopRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

@Service
public class RideService extends AbstractObservableService {

    private final RideRepository rideRepository;
    private final RideStopRepository rideStopRepository;
    private final MongoEventLogger mongoEventLogger;

    public RideService(RideRepository rideRepository,
                       RideStopRepository rideStopRepository,
                       MongoEventLogger mongoEventLogger) {
        this.rideRepository = rideRepository;
        this.rideStopRepository = rideStopRepository;
        this.mongoEventLogger = mongoEventLogger;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    
    public Ride createRide(Ride ride) {
        if (ride.getStatus() == null) {
            ride.setStatus(RideStatus.REQUESTED);
        }
        Ride saved = rideRepository.save(ride);
        notifyObservers("RIDE_CREATED", saved);
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
        return saved;
    }
    public void deleteRide(Long id) {
        Ride ride = getRideById(id);
        rideRepository.delete(ride);
        notifyObservers("RIDE_DELETED", id);
    }

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

        ride.setStatus(RideStatus.CANCELLED);

        if (ride.getDriverId() != null) {
            rideRepository.updateDriverStatusToAvailable(ride.getDriverId());
        }

        Ride saved = rideRepository.save(ride);
        notifyObservers("RIDE_CANCELLED", saved);
        return saved;
    }

    @Transactional
    public Ride completeRide(Long rideId) {
        Ride ride = getRideById(rideId);

        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new InvalidRideStatusException(
                    "Ride status must be IN_PROGRESS to complete. Current status: " + ride.getStatus()
            );
        }

        int driverExists = rideRepository.countDriversById(ride.getDriverId());
        if (driverExists == 0) {
            throw new DriverNotFoundException("Driver not found with id: " + ride.getDriverId());
        }

        int busyDriver = rideRepository.countBusyDriversById(ride.getDriverId());
        if (busyDriver == 0) {
            throw new InvalidRideStatusException(
                    "Ride can only be completed when ride is IN_PROGRESS and driver is BUSY. Current driver status is not BUSY"
            );
        }

        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());

        if (ride.getFare() == null) {
            double calculatedFare = calculateFare(
                    ride.getPickupLatitude(),
                    ride.getPickupLongitude(),
                    ride.getDropoffLatitude(),
                    ride.getDropoffLongitude()
            );
            ride.setFare(calculatedFare);
        }

        rideRepository.updateDriverStatusToAvailable(ride.getDriverId());
        Ride saved = rideRepository.save(ride);

        try {
            rideRepository.createPayment(saved.getId(), saved.getUserId(), saved.getFare());
        } catch (Exception ignored) {
        }

        notifyObservers("RIDE_COMPLETED", saved);
        return saved;
    }

    private double calculateFare(double pickupLat, double pickupLon, double dropoffLat, double dropoffLon) {
        double distance = Math.sqrt(
                Math.pow(dropoffLat - pickupLat, 2) +
                        Math.pow(dropoffLon - pickupLon, 2)
        ) * 111;

        return distance * 15.0;
    }
    public RideWithStopsDTO addStops(Long rideId, List<RideStopRequestDTO> stopRequests) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot add stops to a ride that is not REQUESTED or ACCEPTED");
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
        double completionRate = totalRides > 0 ? ((double) completedRides / totalRides) * 100 : 0.0;

        return new RideAnalyticsDTO(
                totalRides,
                completedRides,
                cancelledRides,
                totalRevenue,
                averageFare,
                completionRate
        );
    }

    @Cacheable(value = "ride-service::S3-F1", key = "(#status != null ? #status.name() : 'ALL') + '_' + #startDate + '_' + #endDate")
    public List<Ride> getRidesByStatusAndDateRange(RideStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        String statusName = status != null ? status.name() : null;
        return rideRepository.getRidesByStatusAndDateRange(startDate, endDate, statusName);
    }

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

        String status = rideRepository.findStatusByDriverId(driverId);
        boolean driverExists = rideRepository.driverexists(driverId);
        if (!driverExists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Driver not found with id: " + driverId);
        }

        if (status.equals("OFFLINE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Driver is not available");
        }
        if (status.equals("BUSY")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Driver with id: " + driverId + " is OFFLINE");
        }

        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.ACCEPTED);
        rideRepository.updateDriverStatusToBusy(driverId);
        Ride saved = rideRepository.save(ride);
        notifyObservers("DRIVER_ASSIGNED", saved);
        return saved;
    }

    @Cacheable(value = "ride-service::S3-F9", key = "#rideId")
    public RideDetailsDTO GetRideDetailsWithStops(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId));

        List<RideStop> stops = rideStopRepository.findByRideIdOrderByStopOrderAsc(rideId);
        return new RideDetailsDTO(ride, stops);
    }
}
