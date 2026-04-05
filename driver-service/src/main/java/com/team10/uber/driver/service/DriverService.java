package com.team10.uber.driver.service;

import com.team10.uber.driver.dto.DriverDocumentAlertDTO;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final DriverDocumentRepository driverDocumentRepository;

    public DriverService(DriverRepository driverRepository, DriverDocumentRepository driverDocumentRepository) {
        this.driverRepository = driverRepository;
        this.driverDocumentRepository = driverDocumentRepository;
    }

    public Driver createDriver(Driver driver) {
        return driverRepository.save(driver);
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public Driver getDriverById(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found with id: " + id));
    }

    public Driver updateDriver(Long id, Driver updatedDriver) {
        Driver existingDriver = getDriverById(id);

        existingDriver.setName(updatedDriver.getName());
        existingDriver.setEmail(updatedDriver.getEmail());
        existingDriver.setPhone(updatedDriver.getPhone());
        existingDriver.setLicenseNumber(updatedDriver.getLicenseNumber());
        existingDriver.setStatus(updatedDriver.getStatus());
        existingDriver.setRating(updatedDriver.getRating());
        existingDriver.setTotalRatings(updatedDriver.getTotalRatings());
        existingDriver.setVehicleDetails(updatedDriver.getVehicleDetails());
        existingDriver.setDriverDocuments(updatedDriver.getDriverDocuments());

        return driverRepository.save(existingDriver);
    }

    public Driver updateVehicleDetails(Long id, Map<String, Object> vehicleUpdates) {
        Driver existingDriver = getDriverById(id); // 404 if not found

        Map<String, Object> merged = new HashMap<>();
        if (existingDriver.getVehicleDetails() != null) {
            merged.putAll(existingDriver.getVehicleDetails());
        }
        if (vehicleUpdates != null) {
            merged.putAll(vehicleUpdates);
        }

        existingDriver.setVehicleDetails(merged);
        return driverRepository.save(existingDriver);
    }

    public void deleteDriver(Long id) {
        Driver driver = getDriverById(id);
        driverRepository.delete(driver);
    }

    @Transactional
    public Driver rateDriver(Long id, Long rideId, Double rating) {
        Driver driver = getDriverById(id);

        Driver rideDriver = driverRepository.findDriverByRideId(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No driver assigned to ride with id: " + rideId));

        String rideStatus = driverRepository.findRideStatusById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ride not found with id: " + rideId));

        if (!driver.getId().equals(rideDriver.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride does not belong to the requested driver");
        }

        if (!rideStatus.equals("COMPLETED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot rate driver for a ride that is not completed");
        }

        if (rating == null || rating < 1.0 || rating > 5.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rating must be between 1.0 and 5.0");
        }

        double totalRatingScore = (driver.getRating() * driver.getTotalRatings()) + rating;
        int updatedTotalRatings = driver.getTotalRatings() + 1;

        driver.setTotalRatings(updatedTotalRatings);
        driver.setRating(totalRatingScore / updatedTotalRatings);

        return driverRepository.save(driver);

    }

    public List<DriverDocumentAlertDTO> getDriversWithExpiredDocuments() {
        List<DriverDocument> expiredDocuments = driverDocumentRepository.findExpiredDocuments();

        // Group expired documents by driver ID
        Map<Long, List<DriverDocument>> groupedByDriver = new HashMap<>();
        for (DriverDocument doc : expiredDocuments) {
            Long driverId = doc.getDriver().getId();
            groupedByDriver.computeIfAbsent(driverId, k -> new ArrayList<>()).add(doc);
        }

        // Build DTOs for each driver with expired documents
        List<DriverDocumentAlertDTO> result = new ArrayList<>();
        for (Long driverId : groupedByDriver.keySet()) {
            Driver driver = getDriverById(driverId);
            List<DriverDocument> docsForDriver = groupedByDriver.get(driverId);
            result.add(new DriverDocumentAlertDTO(driver, docsForDriver));
        }

        return result;
    }
}
