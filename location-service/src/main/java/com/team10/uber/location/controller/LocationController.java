package com.team10.uber.location.controller;

import com.team10.uber.location.dto.DriverMovementSummaryDTO;
import com.team10.uber.location.model.Location;
import com.team10.uber.location.service.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ResponseEntity<?> createLocation(@RequestBody Location location) {
        try {
            Location created = locationService.createLocation(location);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (ResponseStatusException ex) {

            Map<String, Object> error = new HashMap<>();
            error.put("timestamp", LocalDateTime.now());
            error.put("status", ex.getStatusCode().value());
            error.put("error", ex.getStatusCode().toString());
            error.put("message", ex.getReason());

            return ResponseEntity.status(ex.getStatusCode()).body(error);
        }
    }

    @GetMapping("/{id}")
    public Location getLocationById(@PathVariable Long id) {
        return locationService.getLocationById(id);
    }

    @GetMapping
    public List<Location> getAllLocations() {
        return locationService.getAllLocations();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateLocation(@PathVariable Long id, @RequestBody Location location) {
        try {
            Location updated = locationService.updateLocation(id, location);
            return ResponseEntity.ok(updated);

        } catch (ResponseStatusException ex) {

            Map<String, Object> error = new HashMap<>();
            error.put("timestamp", LocalDateTime.now());
            error.put("status", ex.getStatusCode().value());
            error.put("error", ex.getStatusCode().toString());
            error.put("message", ex.getReason());

            return ResponseEntity.status(ex.getStatusCode()).body(error);
        }
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteLocation(@PathVariable Long id) {
        locationService.deleteLocation(id);
        return Map.of("message", "Location deleted successfully");
    }

    

    //S4-F1
    @GetMapping("/driver/{driverId}/latest")
    public Location getLatestLocationByDriverId(@PathVariable Long driverId) {
        return locationService.getLatestLocationByDriverId(driverId);
    }


    //S4-F6
    @GetMapping("/history")
    public List<Location> getLocationsInDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) Long driverId
    ) {
        return locationService.getLocationsInDateRange(startDate, endDate, driverId);
    }
  
    //S4-F8
    @GetMapping("/driver/{driverId}/summary")
    public DriverMovementSummaryDTO getDriverMovementSummary(
            @PathVariable Long driverId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return locationService.getDriverMovementSummary(driverId, startDate, endDate);
    }
}

