package com.team10.uber.ride.controller;

import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.service.RideService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    public Ride createRide(@RequestBody Ride ride) {
        return rideService.createRide(ride);
    }

    @GetMapping
    public List<Ride> getAllRides() {
        return rideService.getAllRides();
    }

    @GetMapping("/{id}")
    public Ride getRideById(@PathVariable Long id) {
        return rideService.getRideById(id);
    }

    @PutMapping("/{id}")
    public Ride updateRide(@PathVariable Long id, @RequestBody Ride updatedRide) {
        return rideService.updateRide(id, updatedRide);
    }

    @PutMapping("/{id}/cancel")
    public Ride cancelRide(@PathVariable Long id) {
        return rideService.cancelRide(id);
    }

    @DeleteMapping("/{id}")
    public String deleteRide(@PathVariable Long id) {
        rideService.deleteRide(id);
        return "Ride deleted successfully";
    }
}