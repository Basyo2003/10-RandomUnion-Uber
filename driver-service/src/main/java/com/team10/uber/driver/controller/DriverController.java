package com.team10.uber.driver.controller;

import com.team10.uber.driver.dto.DriverDocumentAlertDTO;
import com.team10.uber.driver.dto.DriverWithDocumentsDTO;
import com.team10.uber.driver.dto.RateDriverDTO;
import com.team10.uber.driver.dto.VerifyDriverDTO;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.service.DriverDocumentService;
import com.team10.uber.driver.service.DriverService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;
    private final DriverDocumentService driverDocumentService;

    public DriverController(DriverService driverService, DriverDocumentService driverDocumentService) {
        this.driverService = driverService;
        this.driverDocumentService = driverDocumentService;
    }

    @PostMapping
    public Driver createDriver(@RequestBody Driver driver) {
        return driverService.createDriver(driver);
    }

    @GetMapping
    public List<Driver> getAllDrivers() {
        return driverService.getAllDrivers();
    }

    @GetMapping("/{id}")
    public Driver getDriverById(@PathVariable Long id) {
        return driverService.getDriverById(id);
    }

    @GetMapping("/search")
    public List<Driver> searchDrivers(
            @RequestParam(required = false) DriverStatus status,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating) {
        return driverService.searchDrivers(status, minRating, maxRating);
    }

    @PutMapping("/{id}")
    public Driver updateDriver(@PathVariable Long id, @RequestBody Driver updatedDriver) {
        return driverService.updateDriver(id, updatedDriver);
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

    @GetMapping("/documents/expired")
    public List<DriverDocumentAlertDTO> getExpiredDocuments() {
        return driverService.getDriversWithExpiredDocuments();
    }
    @PutMapping("/{driverId}/documents/{documentId}/verify")
    public DriverWithDocumentsDTO verifyDriverDocument(@PathVariable Long driverId, @PathVariable Long documentId,
            @RequestBody VerifyDriverDTO verifyRequest) {
        return driverDocumentService.verifyDriverDocument(driverId, documentId, verifyRequest.getVerifiedBy());
    }

}
