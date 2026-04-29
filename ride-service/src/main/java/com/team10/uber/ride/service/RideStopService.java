package com.team10.uber.ride.service;

import com.team10.uber.ride.model.RideStop;
import com.team10.uber.ride.repository.RideRepository;
import com.team10.uber.ride.repository.RideStopRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RideStopService {

    private final RideStopRepository rideStopRepository;
    private final RideRepository rideRepository;

    public RideStopService(RideStopRepository rideStopRepository, RideRepository rideRepository) {
        this.rideStopRepository = rideStopRepository;
        this.rideRepository = rideRepository;
    }

    public RideStop createRideStop(RideStop rideStop) {
        if (rideStop.getStopOrder() == null || rideStop.getLatitude() == null || rideStop.getLongitude() == null
                || rideStop.getAddress() == null || rideStop.getAddress().isBlank()
                || rideStop.getStatus() == null
                || rideStop.getRide() == null || rideStop.getRide().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required fields: stopOrder, latitude, longitude, address, status, ride.id");
        }
        if (!rideRepository.existsById(rideStop.getRide().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found with id: " + rideStop.getRide().getId());
        }
        return rideStopRepository.save(rideStop);
    }

    public List<RideStop> getAllRideStops() {
        return rideStopRepository.findAll();
    }

    @Cacheable(value = "ride-service::rideStop", key = "#id")
    public RideStop getRideStopById(Long id) {
        return rideStopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RideStop not found with id: " + id));
    }

    public RideStop updateRideStop(Long id, RideStop updatedRideStop) {
        if (updatedRideStop.getStopOrder() == null || updatedRideStop.getLatitude() == null || updatedRideStop.getLongitude() == null
                || updatedRideStop.getAddress() == null || updatedRideStop.getAddress().isBlank()
                || updatedRideStop.getStatus() == null
                || updatedRideStop.getRide() == null || updatedRideStop.getRide().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required fields: stopOrder, latitude, longitude, address, status, ride.id");
        }
        if (!rideRepository.existsById(updatedRideStop.getRide().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found with id: " + updatedRideStop.getRide().getId());
        }
        RideStop existing = getRideStopById(id);
        existing.setStopOrder(updatedRideStop.getStopOrder());
        existing.setLatitude(updatedRideStop.getLatitude());
        existing.setLongitude(updatedRideStop.getLongitude());
        existing.setAddress(updatedRideStop.getAddress());
        existing.setStatus(updatedRideStop.getStatus());
        existing.setMetadata(updatedRideStop.getMetadata());
        existing.setRide(updatedRideStop.getRide());
        return rideStopRepository.save(existing);
    }

    public void deleteRideStop(Long id) {
        RideStop rideStop = getRideStopById(id);
        rideStopRepository.delete(rideStop);
    }
}
