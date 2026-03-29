package com.team10.uber.location.controller;

import com.team10.uber.location.model.Location;
import com.team10.uber.location.service.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
}