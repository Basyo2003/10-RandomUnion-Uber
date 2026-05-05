package com.team10.uber.driver.service;

import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.dto.DriverWithDocumentsDTO;
import com.team10.uber.driver.observer.AbstractObservableService;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import jakarta.annotation.PostConstruct;
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
public class DriverDocumentService extends AbstractObservableService {

    private static final long TTL_DETAIL = 15 * 60; // §4.4.2 — 15 min

    private final DriverDocumentRepository driverDocumentRepository;
    private final DriverRepository driverRepository;
    private final MongoEventLogger mongoEventLogger;
    private final RedisCacheService cache;

    public DriverDocumentService(DriverDocumentRepository driverDocumentRepository, DriverRepository driverRepository,
                                 MongoEventLogger mongoEventLogger, RedisCacheService cache) {
        this.driverDocumentRepository = driverDocumentRepository;
        this.driverRepository = driverRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cache = cache;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    // ── §4.4.4 — Cache invalidation helpers ─────────────────────────────────

    private void evictDocumentCaches(Long documentId) {
        cache.evict("driver-document::" + documentId);
        cache.evictByPattern("S2-F9::*");
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public DriverDocument createDriverDocument(Long driverId, DriverDocument driverDocument) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + driverId));
        driverDocument.setDriver(driver);
        DriverDocument saved = driverDocumentRepository.save(driverDocument);
        notifyObservers("DRIVER_CREATED", driver);
        cache.evictByPattern("S2-F9::*");
        return saved;
    }

    public List<DriverDocument> getAllDriverDocuments() {
        return driverDocumentRepository.findAll();
    }

    public List<DriverDocument> getDriverDocumentsByDriverId(Long driverId) {
        driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + driverId));
        return driverDocumentRepository.findByDriverId(driverId);
    }

    /** §4.4.2 — GET /api/driver-documents/{id}, cached 15 min. */
    public DriverDocument getDriverDocumentById(Long id) {
        String key = "driver-document::" + id;
        DriverDocument cached = cache.get(key, DriverDocument.class);
        if (cached != null) return cached;

        DriverDocument doc = driverDocumentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver document not found with id: " + id));
        cache.put(key, doc, TTL_DETAIL);
        return doc;
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

        DriverDocument saved = driverDocumentRepository.save(existingDriverDocument);
        notifyObservers("UPDATED", saved.getDriver());
        evictDocumentCaches(id);
        return saved;
    }

    public void deleteDriverDocument(Long id) {
        DriverDocument driverDocument = getDriverDocumentById(id);
        Long driverId = driverDocument.getDriver() != null
                ? driverDocument.getDriver().getId()
                : null;
        driverDocumentRepository.delete(driverDocument);
        notifyObservers("DRIVER_DELETED", driverId);
        evictDocumentCaches(driverDocument.getId());
    }

    @Transactional
    public DriverWithDocumentsDTO verifyDriverDocument(Long driverId, Long documentId, Long verifiedBy) {
        // Role check first — ensures 403 is returned before any 400 validation
        if (verifiedBy == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Verifier user ID must be provided");

        String userRole = driverDocumentRepository.findUserRoleById(verifiedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Verifier user not found"));

        if (!"ADMIN".equals(userRole))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only ADMIN users can verify documents");

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver not found with id: " + driverId));

        DriverDocument document = driverDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Driver document not found with id: " + documentId));

        if (!document.getDriver().getId().equals(driverId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Document does not belong to this driver");

        if (document.getExpiryDate().isBefore(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document has expired");

        if (Boolean.TRUE.equals(document.getVerified()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document is already verified");

        document.setVerified(true);
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null)
            metadata = new HashMap<>();
        metadata.put("verifiedAt", LocalDateTime.now().toString());
        metadata.put("verifiedBy", verifiedBy);
        document.setMetadata(metadata);

        driverDocumentRepository.save(document);
        notifyObservers("DOCUMENT_VERIFIED", driver);
        evictDocumentCaches(documentId);
        cache.evict("driver::" + driverId);

        return new DriverWithDocumentsDTO(driver, driver.getDriverDocuments());
    }
}
