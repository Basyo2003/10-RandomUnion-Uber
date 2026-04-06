package com.team10.uber.ride.service;

import com.team10.uber.ride.dto.FareEstimateDTO;
import com.team10.uber.ride.dto.FareEstimateRequestDTO;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.repository.RideRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RideService {

    private final RideRepository rideRepository;

    public RideService(RideRepository rideRepository) {
        this.rideRepository = rideRepository;
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
}