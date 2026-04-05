package com.team10.uber.driver.service;

import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.dto.DriverWithDocumentsDTO;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + driverId));

        driverDocument.setDriver(driver);
        return driverDocumentRepository.save(driverDocument);
    }

    public List<DriverDocument> getAllDriverDocuments() {
        return driverDocumentRepository.findAll();
    }

    public DriverDocument getDriverDocumentById(Long id) {
        return driverDocumentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver document not found with id: " + id));
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
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Driver not found with id: " + updatedDriverDocument.getDriver().getId()));
            existingDriverDocument.setDriver(driver);
        }

        return driverDocumentRepository.save(existingDriverDocument);
    }

    public void deleteDriverDocument(Long id) {
        DriverDocument driverDocument = getDriverDocumentById(id);
        driverDocumentRepository.delete(driverDocument);
    }

    @Transactional
    public DriverWithDocumentsDTO verifyDriverDocument(Long driverId, Long documentId, Long verifiedBy) {
        // Step 1: Find driver by ID → 404 if not found
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + driverId));

        // Step 2: Find document by ID → 404 if not found
        DriverDocument document = driverDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver document not found with id: " + documentId));

        // Step 3: Verify document ownership → 400 if not
        if (!document.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Document does not belong to this driver");
        }

        // Step 4: Check document not expired → 400 if expired
        if (document.getExpiryDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Document has expired");
        }

        // Step 5: Check document not already verified → 400 if verified
        if (Boolean.TRUE.equals(document.getVerified())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Document is already verified");
        }

        // Step 6: Verify verifiedBy is ADMIN user → 403 if not
        if (verifiedBy == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Verifier user ID must be provided");
        }
        String userRole = driverDocumentRepository.findUserRoleById(verifiedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Verifier user not found"));

        if (!"ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only ADMIN users can verify documents");
        }

        // Step 7: Update metadata and set verified
        document.setVerified(true);

        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put("verifiedAt", LocalDateTime.now().toString());
        metadata.put("verifiedBy", verifiedBy);
        document.setMetadata(metadata);

        // Persist the document
        driverDocumentRepository.save(document);

        // Step 8: Return the full driver with all documents
        return new DriverWithDocumentsDTO(driver, driver.getDriverDocuments());

    }
}
