package com.team10.uber.driver.controller;

import com.team10.uber.contracts.dto.DriverAvailabilityDTO;
import com.team10.uber.driver.dto.DriverDashboardDTO;
import com.team10.uber.driver.dto.TopRatedDriverDTO;
import com.team10.uber.driver.dto.DriverDocumentAlertDTO;
import com.team10.uber.driver.dto.DriverEarningsDTO;
import com.team10.uber.driver.dto.DriverWithDocumentsDTO;
import com.team10.uber.driver.dto.RateDriverDTO;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.service.DriverDocumentService;
import com.team10.uber.driver.service.DriverIndexService;
import com.team10.uber.driver.service.DriverService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;
    private final DriverDocumentService driverDocumentService;
    private final DriverIndexService driverIndexService;

    public DriverController(DriverService driverService,
                            DriverDocumentService driverDocumentService,
                            DriverIndexService driverIndexService) {
        this.driverService = driverService;
        this.driverDocumentService = driverDocumentService;
        this.driverIndexService = driverIndexService;
    }

    @PostMapping
    public Driver createDriver(@RequestBody Driver driver) {
        return driverService.createDriver(driver);
    }

    @GetMapping
    public List<Driver> getAllDrivers() {
        return driverService.getAllDrivers();
    }

    @GetMapping("/search")
    public List<Driver> searchDrivers(
            @RequestParam(required = false) DriverStatus status,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating) {
        return driverService.searchDrivers(status, minRating, maxRating);
    }

    @GetMapping("/search/full-text")
    public List<Driver> searchDriversFullText(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) DriverStatus status,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating) {
        return driverService.fullTextSearchDrivers(query, vehicleType, status, minRating, maxRating);
    }

    @GetMapping("/vehicle-type")
    public List<Driver> filterByVehicleType(
            @RequestParam String type,
            @RequestParam(required = false) String status) {

        return driverService.filterByVehicleType(type, status);
    }

    @GetMapping("/reports/top-rated")
    public List<TopRatedDriverDTO> getTopRatedDrivers(
            @RequestParam(defaultValue = "5") int limit) {

        return driverService.getTopRatedDrivers(limit);
    }

    @GetMapping("/{id}/dashboard")
    public DriverDashboardDTO getDriverDashboard(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long callerId,
            @RequestHeader("X-User-Role") String callerRole) {
        return driverService.getDriverDashboard(id, callerId, callerRole);
    }

    @GetMapping("/{id}")
    public Driver getDriverById(@PathVariable Long id) {
        return driverService.getDriverById(id);
    }

    @GetMapping("/{id}/availability")
    public DriverAvailabilityDTO getDriverAvailability(@PathVariable Long id) {
        return driverService.getDriverAvailability(id);
    }

    @GetMapping("/{id}/earnings")
    public DriverEarningsDTO getDriverEarningsSummary(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-User-Id") Long callerId,
            @RequestHeader("X-User-Role") String callerRole) {
        return driverService.getDriverEarningsSummary(id, startDate, endDate, callerId, callerRole);
    }

    @PutMapping("/{id}/vehicle")
    public Driver updateVehicleDetails(@PathVariable Long id, @RequestBody Map<String, Object> vehicleUpdates) {
        return driverService.updateVehicleDetails(id, vehicleUpdates);
    }

    @PutMapping("/{id}/availability")
    public ResponseEntity<Void> updateAvailability(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        DriverStatus status;
        try {
            status = DriverStatus.valueOf(body.get("status"));
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid status value");
        }

        driverService.updateDriverAvailability(id, status);

        return ResponseEntity.ok().build(); // ✅ only 200
    }

    @PutMapping("/{id}")
    public Driver updateDriver(@PathVariable Long id, @RequestBody Driver updatedDriver) {
        return driverService.updateDriver(id, updatedDriver);
    }

    @PostMapping("/{id}/index")
    public ResponseEntity<Void> indexDriver(@PathVariable Long id) {
        driverIndexService.indexDriver(id, "explicit");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public String deleteDriver(@PathVariable Long id) {
        driverService.deleteDriver(id);
        return "Driver deleted successfully";
    }

    @PostMapping("/{id}/rate")
    public Driver rateDriver(@PathVariable Long id, @RequestBody RateDriverDTO rateDriverDTO) {
        return driverService.rateDriver(id, rateDriverDTO.getRideId(), rateDriverDTO.getRating());
    }

    @PostMapping("/{driverId}/documents")
    public DriverDocument createDocument(@PathVariable Long driverId, @RequestBody DriverDocument document) {
        return driverDocumentService.createDriverDocument(driverId, document);
    }

    @GetMapping("/{driverId}/documents")
    public List<DriverDocument> getDriverDocuments(@PathVariable Long driverId) {
        return driverDocumentService.getDriverDocumentsByDriverId(driverId);
    }

    @DeleteMapping("/{driverId}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long driverId, @PathVariable Long documentId) {
        driverDocumentService.deleteDriverDocument(documentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/documents/expired")
    public List<DriverDocumentAlertDTO> getExpiredDocuments() {
        return driverService.getDriversWithExpiredDocuments();
    }

    @PutMapping("/{driverId}/documents/{documentId}/verify")
    public ResponseEntity<DriverWithDocumentsDTO> verifyDriverDocument(
            @PathVariable Long driverId,
            @PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long callerId) {
        DriverWithDocumentsDTO result = driverDocumentService.verifyDriverDocument(driverId, documentId, callerId);
        return ResponseEntity.ok(result);
    }

}
