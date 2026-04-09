package com.team10.uber.ride.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.exception.DriverNotFoundException;
import com.team10.uber.ride.exception.InvalidRideStatusException;
import com.team10.uber.ride.exception.RideNotFoundException;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.repository.RideRepository;

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
        return rideRepository.save(existing);
    }

    public void deleteRide(Long id) {
        Ride ride = getRideById(id);
        rideRepository.delete(ride);
    }

    @Transactional
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
        int driverExists = rideRepository.countDriverById(ride.getDriverId());
        if (driverExists == 0) {
            throw new DriverNotFoundException("Driver not found with id: " + ride.getDriverId());
        }

        int busyDriver = rideRepository.countBusyDriverById(ride.getDriverId());
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
}