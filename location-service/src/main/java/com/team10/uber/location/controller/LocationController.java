package com.team10.uber.location.controller;

import com.team10.uber.location.dto.*;

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
    public ResponseEntity<?> getLocationById(@PathVariable Long id) {
        try {
            Location location = locationService.getLocationById(id);
            return ResponseEntity.ok(location);

        } catch (ResponseStatusException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("timestamp", LocalDateTime.now());
            error.put("status", ex.getStatusCode().value());
            error.put("error", ex.getStatusCode().toString());
            error.put("message", ex.getReason());
            error.put("path", "/api/locations/" + id);

            return ResponseEntity.status(ex.getStatusCode()).body(error);
        }
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


    // S4-F2
    @PostMapping("/driver/{driverId}")
    public ResponseEntity<Location> createLocationForDriver(
            @PathVariable Long driverId,
            @RequestBody Map<String, Object> body
    ) {
        if (body.get("latitude") == null || body.get("longitude") == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Latitude and longitude are required"
            );
        }

        Double latitude = Double.valueOf(body.get("latitude").toString());
        Double longitude = Double.valueOf(body.get("longitude").toString());

        Map<String, Object> metadata = (Map<String, Object>) body.get("metadata");

        Location location = new Location();
        location.setDriverId(driverId);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setMetadata(metadata);

        Location created = locationService.createLocationForDriver(driverId, location);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
  
    //S4-F3
    @GetMapping("/nearby")
    public List<NearbyDriverDTO> getNearbyDrivers(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam Double radiusKm
    ) {
        return locationService.findNearbyAvailableDrivers(lat, lon, radiusKm);
    }



    //S4-F4
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> batchCreateLocations(@RequestBody BatchLocationRequest request) {
        return locationService.batchCreateLocations(request);
    }

    //S4-F5
    @GetMapping("/metadata/search")
    public List<Location> filterLocationsByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value
    ) {
        return locationService.filterLocationsByMetadata(key, operator, value);
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
  
    //S4-F7
    @DeleteMapping("/purge")
    public Map<String, Integer> purgeOldLocations(@RequestParam int olderThanDays) {
        int deletedCount = locationService.purgeOldLocations(olderThanDays);
        return Map.of("deletedCount", deletedCount);
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

    //S4-F9
    @GetMapping("/stationary")
    public ResponseEntity<?> getStationaryDrivers(
            @RequestParam Double maxSpeed,
            @RequestParam Integer sinceMinutes
    ) {
        try {
            List<StationaryDriverDTO> result =
                    locationService.getStationaryDrivers(maxSpeed, sinceMinutes);
            return ResponseEntity.ok(result);

        } catch (ResponseStatusException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("timestamp", LocalDateTime.now());
            error.put("status", ex.getStatusCode().value());
            error.put("error", ex.getStatusCode().toString());
            error.put("message", ex.getReason());

            return ResponseEntity.status(ex.getStatusCode()).body(error);
        }
    }


    // S4-F10
    @GetMapping("/analytics")
    public LocationAnalyticsDTO getLocationAnalytics(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return locationService.getLocationAnalytics(startDate, endDate);
    }


    // S4-F11
    @PostMapping("/{driverId}/tracking")
    public ResponseEntity<?> recordLocationGpsEvent(
            @PathVariable Long driverId,
            @RequestBody TrackingEventRequest request
    ) {
        try {
            locationService.recordLocationGpsEvent(driverId, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "message", "Tracking event recorded successfully",
                            "driverId", driverId
                    )
            );

        } catch (ResponseStatusException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("timestamp", LocalDateTime.now());
            error.put("status", ex.getStatusCode().value());
            error.put("error", ex.getStatusCode().toString());
            error.put("message", ex.getReason());
            error.put("path", "/api/locations/" + driverId + "/tracking");

            return ResponseEntity.status(ex.getStatusCode()).body(error);
        }
    }

    // S4-F12
    @GetMapping("/{driverId}/tracking")
    public ResponseEntity<?> getLocationTrackingTimeline(
            @PathVariable Long driverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        try {
            List<LocationTrackingEventDTO> result =
                    locationService.getLocationTrackingTimeline(driverId, startTime, endTime);

            return ResponseEntity.ok(result);

        } catch (ResponseStatusException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("timestamp", LocalDateTime.now());
            error.put("status", ex.getStatusCode().value());
            error.put("error", ex.getStatusCode().toString());
            error.put("message", ex.getReason());
            error.put("path", "/api/locations/" + driverId + "/tracking");

            return ResponseEntity.status(ex.getStatusCode()).body(error);
        }
    }
}

