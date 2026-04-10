package com.team10.uber.ride.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.exception.DriverNotFoundException;
import com.team10.uber.ride.exception.InvalidRideStatusException;
import com.team10.uber.ride.exception.RideNotFoundException;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.repository.RideRepository;
import com.team10.uber.ride.dto.RideStopRequestDTO;
import com.team10.uber.ride.dto.RideWithStopsDTO;
import com.team10.uber.ride.enums.StopStatus;
import com.team10.uber.ride.dto.FareEstimateDTO;
import com.team10.uber.ride.dto.FareEstimateRequestDTO;
import com.team10.uber.ride.dto.RideAnalyticsDTO;
import com.team10.uber.ride.model.RideStop;
import com.team10.uber.ride.repository.RideRepository;
import com.team10.uber.ride.repository.RideStopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.ride.dto.RideDetailsDTO;

import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final RideStopRepository rideStopRepository;

    public RideService(RideRepository rideRepository, RideStopRepository rideStopRepository) {
        this.rideRepository = rideRepository;
        this.rideStopRepository = rideStopRepository;
    }

    public Ride createRide(Ride ride) {
        if (ride.getUserId() == null || ride.getPickupLatitude() == null || ride.getPickupLongitude() == null
                || ride.getDropoffLatitude() == null || ride.getDropoffLongitude() == null || ride.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required fields: userId, pickupLatitude, pickupLongitude, dropoffLatitude, dropoffLongitude, status");
        }
        if (rideRepository.countUsersById(ride.getUserId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + ride.getUserId());
        }
        if (ride.getDriverId() != null && rideRepository.countDriversById(ride.getDriverId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found with id: " + ride.getDriverId());
        }
        return rideRepository.save(ride);
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public Ride getRideById(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + id));
    }

    public Ride updateRide(Long id, Ride updatedRide) {
        if (updatedRide.getUserId() == null || updatedRide.getPickupLatitude() == null || updatedRide.getPickupLongitude() == null
                || updatedRide.getDropoffLatitude() == null || updatedRide.getDropoffLongitude() == null || updatedRide.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required fields: userId, pickupLatitude, pickupLongitude, dropoffLatitude, dropoffLongitude, status");
        }
        if (rideRepository.countUsersById(updatedRide.getUserId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + updatedRide.getUserId());
        }
        if (updatedRide.getDriverId() != null && rideRepository.countDriversById(updatedRide.getDriverId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found with id: " + updatedRide.getDriverId());
        }
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
        return rideRepository.save(existing);
    }

    public void deleteRide(Long id) {
        Ride ride = getRideById(id);
        rideRepository.delete(ride);
    }

    @Transactional
    public Ride cancelRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId));

        RideStatus currentStatus = ride.getStatus();
        if (currentStatus != RideStatus.REQUESTED && currentStatus != RideStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ride can only be cancelled when status is REQUESTED or ACCEPTED. Current status: "
                    + currentStatus);
        }

        if (ride.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride can only be cancelled when status is REQUESTED or ACCEPTED and driver is BUSY");
        }

        int busyDriver = rideRepository.countBusyDriversById(ride.getDriverId());
        if (busyDriver == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride can only be cancelled when status is REQUESTED or ACCEPTED and driver is BUSY");
        }

        ride.setStatus(RideStatus.CANCELLED);

        int updatedRows = rideRepository.updateDriverStatusToAvailable(ride.getDriverId());
        if (updatedRows == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride can only be cancelled when status is REQUESTED or ACCEPTED and driver is BUSY");
        }

        return rideRepository.save(ride);
    }
    public Ride completeRide(Long rideId) {
        // 1) Find ride
        Ride ride = getRideById(rideId);

        // 2) Validate status
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new InvalidRideStatusException(
                    "Ride status must be IN_PROGRESS to complete. Current status: " + ride.getStatus()
            );
        }

        // 3) Validate driver exists and is assigned to this ride
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

        // 4) Set ride as completed
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());

        // 5) Calculate fare if unset
        if (ride.getFare() == null) {
            double calculatedFare = calculateFare(
                    ride.getPickupLatitude(),
                    ride.getPickupLongitude(),
                    ride.getDropoffLatitude(),
                    ride.getDropoffLongitude()
            );
            ride.setFare(calculatedFare);
        }

        // 6) Update driver status to AVAILABLE
        int updatedRows = rideRepository.updateDriverStatusToAvailable(ride.getDriverId());
        if (updatedRows == 0) {
            throw new InvalidRideStatusException(
                    "Ride can only be completed when ride is IN_PROGRESS and driver is BUSY"
            );
        }

        // 7) Create payment
        rideRepository.createPayment(
                ride.getId(),
                ride.getUserId(),
                ride.getFare()
        );

        // 8) Save and return updated ride
        return rideRepository.save(ride);
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add stops to a ride that is not REQUESTED or ACCEPTED");
        }

        for (RideStopRequestDTO req : stopRequests) {
            if (req.getLatitude() == null || req.getLongitude() == null || req.getAddress() == null || req.getAddress().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each stop must have latitude, longitude, and address");
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
        return new RideWithStopsDTO(ride, allStops);
    }
  
    public List<Ride> filterByMetadata(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key and value must not be blank");
        }
        return rideRepository.findByMetadataField(key, value);
    }
  
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
        if (nearbyCount <= 10) surgeMultiplier = 1.0;
        else if (nearbyCount <= 20) surgeMultiplier = 1.5;
        else surgeMultiplier = 2.0;

        double fare = 15.0 * distance * surgeMultiplier;

        return new FareEstimateDTO(distance, duration, fare, surgeMultiplier);
    }
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


    public RideDetailsDTO GetRideDetailsWithStops(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId));


        List<RideStop> stops = rideStopRepository.findByRideIdOrderByStopOrderAsc(rideId);

        return new RideDetailsDTO(ride,stops);
    @Transactional
    public Ride assignDriver(Long rideId, Long driverId) {


        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ride not found with id: " + rideId));


        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new InvalidRideStatusException(
                    "Ride " + rideId + " cannot be assigned. " +
                            "Expected REQUESTED but was: " + ride.getStatus()
            );
        }


        if (!rideRepository.driverexists(driverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Driver not found with id: " + driverId);
        }

        if (rideRepository.countAvailableDriversById(driverId) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Driver is not available");
        }

        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.ACCEPTED);
        rideRepository.save(ride);

        rideRepository.updateDriverStatusToBusy(driverId);

        return ride;
}

    public List<Ride> GetRidesByStatusandDateRange(RideStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        String statusName = status != null ? status.name() : null;
        return rideRepository.GetRidesByStatusandDateRange(startDate, endDate, statusName);
    }
}