package com.team10.uber.ride.controller;

import com.team10.uber.ride.model.RideStop;
import com.team10.uber.ride.service.RideStopService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ride-stops")
public class RideStopController {

    private final RideStopService rideStopService;

    public RideStopController(RideStopService rideStopService) {
        this.rideStopService = rideStopService;
    }

    @PostMapping
    public RideStop createRideStop(@RequestBody RideStop rideStop) {
        return rideStopService.createRideStop(rideStop);
    }

    @GetMapping
    public List<RideStop> getAllRideStops() {
        return rideStopService.getAllRideStops();
    }

    @GetMapping("/{id}")
    public RideStop getRideStopById(@PathVariable Long id) {
        return rideStopService.getRideStopById(id);
    }

    @PutMapping("/{id}")
    public RideStop updateRideStop(@PathVariable Long id, @RequestBody RideStop updatedRideStop) {
        return rideStopService.updateRideStop(id, updatedRideStop);
    }

    @DeleteMapping("/{id}")
    public String deleteRideStop(@PathVariable Long id) {
        rideStopService.deleteRideStop(id);
        return "RideStop deleted successfully";
    }
}
