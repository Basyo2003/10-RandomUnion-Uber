package com.team10.uber.ride.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.team10.uber.ride.enums.RideStatus;
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
    public Ride cancelRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId));

        RideStatus currentStatus = ride.getStatus();
        if (currentStatus != RideStatus.REQUESTED && currentStatus != RideStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ride can only be cancelled when status is REQUESTED or ACCEPTED. Current status: "
                    + currentStatus);
        }

        ride.setStatus(RideStatus.CANCELLED);

        if (ride.getDriverId() != null) {
            if (rideRepository.countDriversById(ride.getDriverId()) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + ride.getDriverId());
            }

            int updatedRows = rideRepository.updateDriverStatusToAvailable(ride.getDriverId());
            if (updatedRows == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + ride.getDriverId());
            }
        }

        return rideRepository.save(ride);
    }
}