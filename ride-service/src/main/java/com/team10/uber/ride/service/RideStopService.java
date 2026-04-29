package com.team10.uber.ride.service;

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

import java.util.List;

@Service
public class RideStopService extends AbstractObservableService {

    private final RideStopRepository rideStopRepository;
    private final RideRepository rideRepository;
    private final MongoEventLogger mongoEventLogger;

    public RideStopService(RideStopRepository rideStopRepository,
                           RideRepository rideRepository,
                           MongoEventLogger mongoEventLogger) {
        this.rideStopRepository = rideStopRepository;
        this.rideRepository = rideRepository;
        this.mongoEventLogger = mongoEventLogger;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
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
        RideStop saved = rideStopRepository.save(rideStop);
        notifyObservers("STOPS_ADDED", saved.getRide());
        return saved;
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
        RideStop saved = rideStopRepository.save(existing);
        notifyObservers("RIDE_UPDATED", saved.getRide());
        return saved;
    }

    public void deleteRideStop(Long id) {
        RideStop rideStop = getRideStopById(id);
        Long rideId = rideStop.getRide() != null ? rideStop.getRide().getId() : null;
        rideStopRepository.delete(rideStop);
        notifyObservers("RIDE_DELETED", rideId);
    }
}
