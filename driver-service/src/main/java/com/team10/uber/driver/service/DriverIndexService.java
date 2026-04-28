package com.team10.uber.driver.service;

import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.elastic.DriverSearchDocument;
import com.team10.uber.driver.observer.AbstractObservableService;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverRepository;
import com.team10.uber.driver.repository.elastic.DriverSearchRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Handles all Elasticsearch indexing for drivers.
 * Kept separate from DriverService to avoid a circular dependency:
 *   DriverEntityListener → DriverIndexService → DriverRepository (PG) + DriverSearchRepository (ES)
 *   DriverService        → DriverRepository (PG)  — no reference to DriverIndexService
 */
@Service
public class DriverIndexService extends AbstractObservableService {

    private final DriverRepository driverRepository;
    private final DriverSearchRepository searchRepository;
    private final MongoEventLogger mongoEventLogger;

    public DriverIndexService(DriverRepository driverRepository,
                               DriverSearchRepository searchRepository,
                               MongoEventLogger mongoEventLogger) {
        this.driverRepository = driverRepository;
        this.searchRepository = searchRepository;
        this.mongoEventLogger = mongoEventLogger;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    /**
     * Index a single driver into Elasticsearch and fire an INDEXED event.
     * source values: "explicit" (POST /index endpoint), "auto_crud_create", "auto_crud_update"
     */
    public void indexDriver(Long driverId, String source) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Driver not found: " + driverId));

        String description = "";
        String vehicleType = "";
        if (driver.getVehicleDetails() != null) {
            Object desc = driver.getVehicleDetails().get("description");
            description = desc != null ? desc.toString() : "";
            Object vt   = driver.getVehicleDetails().get("vehicleType");
            vehicleType = vt != null ? vt.toString() : "";
        }

        DriverSearchDocument doc = new DriverSearchDocument(
                String.valueOf(driver.getId()),
                driver.getName(),
                vehicleType,
                description,
                driver.getRating(),
                driver.getStatus() != null ? driver.getStatus().name() : null
        );
        searchRepository.save(doc);

        List<String> indexedFields = List.of("id", "name", "vehicleType", "description", "rating", "status");
        notifyObservers("INDEXED", Map.of(
                "driverId",      driverId,
                "source",        source,
                "indexedFields", indexedFields
        ));
    }

    /**
     * Remove a driver from Elasticsearch. Called by DriverEntityListener @PostRemove.
     * Soft dependency — ES failure must not crash the delete flow.
     */
    public void removeFromIndex(Long driverId) {
        try {
            searchRepository.deleteById(String.valueOf(driverId));
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(DriverIndexService.class)
                    .warn("[DriverIndexService] Failed to remove driver {} from ES: {}",
                            driverId, ex.getMessage());
        }
    }
}
