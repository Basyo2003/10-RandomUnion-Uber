package com.team10.uber.ride.service;

import com.team10.uber.ride.model.RideStop;
import com.team10.uber.ride.repository.RideStopRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RideStopService {

    private final RideStopRepository rideStopRepository;

    public RideStopService(RideStopRepository rideStopRepository) {
        this.rideStopRepository = rideStopRepository;
    }

    public RideStop createRideStop(RideStop rideStop) {
        return rideStopRepository.save(rideStop);
    }

    public List<RideStop> getAllRideStops() {
        return rideStopRepository.findAll();
    }

    public RideStop getRideStopById(Long id) {
        return rideStopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RideStop not found with id: " + id));
    }

    public RideStop updateRideStop(Long id, RideStop updatedRideStop) {
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
