package com.team10.uber.ride.service;

import com.team10.uber.ride.dto.RideStopRequestDTO;
import com.team10.uber.ride.dto.RideWithStopsDTO;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.enums.StopStatus;
import com.team10.uber.ride.dto.FareEstimateDTO;
import com.team10.uber.ride.dto.FareEstimateRequestDTO;
import com.team10.uber.ride.dto.RideAnalyticsDTO;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.model.RideStop;
import com.team10.uber.ride.repository.RideRepository;
import com.team10.uber.ride.repository.RideStopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
        return rideRepository.save(ride);
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public Ride getRideById(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + id));
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
        return rideRepository.save(existing);
    }

    public void deleteRide(Long id) {
        Ride ride = getRideById(id);
        rideRepository.delete(ride);
    }

    @Transactional
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
}