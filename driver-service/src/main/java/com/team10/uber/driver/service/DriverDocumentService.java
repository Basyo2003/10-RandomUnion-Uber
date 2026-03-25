package com.team10.uber.driver.service;

import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverDocumentService {

    private final DriverDocumentRepository driverDocumentRepository;
    private final DriverRepository driverRepository;

    public DriverDocumentService(DriverDocumentRepository driverDocumentRepository, DriverRepository driverRepository) {
        this.driverDocumentRepository = driverDocumentRepository;
        this.driverRepository = driverRepository;
    }

    public DriverDocument createDriverDocument(Long driverId, DriverDocument driverDocument) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId));

        driverDocument.setDriver(driver);
        return driverDocumentRepository.save(driverDocument);
    }

    public List<DriverDocument> getAllDriverDocuments() {
        return driverDocumentRepository.findAll();
    }

    public DriverDocument getDriverDocumentById(Long id) {
        return driverDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Driver document not found with id: " + id));
    }

    public DriverDocument updateDriverDocument(Long id, DriverDocument updatedDriverDocument) {
        DriverDocument existingDriverDocument = getDriverDocumentById(id);

        existingDriverDocument.setType(updatedDriverDocument.getType());
        existingDriverDocument.setDocumentUrl(updatedDriverDocument.getDocumentUrl());
        existingDriverDocument.setExpiryDate(updatedDriverDocument.getExpiryDate());
        existingDriverDocument.setVerified(updatedDriverDocument.getVerified());
        existingDriverDocument.setMetadata(updatedDriverDocument.getMetadata());

        if (updatedDriverDocument.getDriver() != null && updatedDriverDocument.getDriver().getId() != null) {
            Driver driver = driverRepository.findById(updatedDriverDocument.getDriver().getId())
                    .orElseThrow(() -> new RuntimeException("Driver not found with id: " + updatedDriverDocument.getDriver().getId()));
            existingDriverDocument.setDriver(driver);
        }

        return driverDocumentRepository.save(existingDriverDocument);
    }

    public void deleteDriverDocument(Long id) {
        DriverDocument driverDocument = getDriverDocumentById(id);
        driverDocumentRepository.delete(driverDocument);
    }
}
