package com.team10.uber.driver.service;

import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.messaging.DriverEventPublisher;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for S2-F2: Update Vehicle Details (JSONB Partial Update) */
class DriverServiceS2F2Test {

    private DriverRepository driverRepository;
    private RedisCacheService cache;
    private DriverService driverService;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        cache = mock(RedisCacheService.class);
        driverService = new DriverService(
                driverRepository,
                mock(DriverDocumentRepository.class),
                mock(MongoEventLogger.class),
                cache,
                mock(ElasticsearchOperations.class),
                mock(RideServiceClient.class),
                mock(DriverEventPublisher.class)
        );
        driverService.initObservers();
    }

    private Driver driverWithVehicle(Long id, Map<String, Object> vehicleDetails) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setStatus(DriverStatus.AVAILABLE);
        d.setVehicleDetails(new HashMap<>(vehicleDetails));
        return d;
    }

    // ── Spec scenario: merge existing fields + update + add new ──────────────

    @Test
    @DisplayName("Spec scenario — color updated, new field added, others kept")
    void updateVehicle_mergesFieldsCorrectly() {
        Map<String, Object> existing = Map.of("make", "Toyota", "color", "White", "year", 2022);
        Driver d = driverWithVehicle(1L, existing);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));
        when(driverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cache.get("driver::1", Driver.class)).thenReturn(null);

        Map<String, Object> updates = Map.of("color", "Black", "seats", 4);
        Driver result = driverService.updateVehicleDetails(1L, updates);

        assertThat(result.getVehicleDetails())
                .containsEntry("make", "Toyota")
                .containsEntry("color", "Black")   // updated
                .containsEntry("year", 2022)
                .containsEntry("seats", 4);        // new field
    }

    @Test
    @DisplayName("Spec scenario — driver not found throws 404")
    void updateVehicle_driverNotFound_throws404() {
        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.updateVehicleDetails(999L, Map.of("color", "Red")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Null vehicleDetails initialised to new map with incoming fields")
    void updateVehicle_nullExistingDetails_createsNewMap() {
        Driver d = new Driver();
        d.setId(2L);
        d.setVehicleDetails(null);
        when(driverRepository.findById(2L)).thenReturn(Optional.of(d));
        when(driverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cache.get("driver::2", Driver.class)).thenReturn(null);

        Driver result = driverService.updateVehicleDetails(2L, Map.of("make", "Honda"));

        assertThat(result.getVehicleDetails()).containsEntry("make", "Honda");
    }

    @Test
    @DisplayName("Null update map — vehicleDetails unchanged")
    void updateVehicle_nullUpdateMap_keepsExistingFields() {
        Map<String, Object> existing = Map.of("make", "Toyota", "color", "White");
        Driver d = driverWithVehicle(1L, existing);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));
        when(driverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cache.get("driver::1", Driver.class)).thenReturn(null);

        Driver result = driverService.updateVehicleDetails(1L, null);

        assertThat(result.getVehicleDetails())
                .containsEntry("make", "Toyota")
                .containsEntry("color", "White");
    }

    @Test
    @DisplayName("Cache evicted after update")
    void updateVehicle_cacheEvicted() {
        Driver d = driverWithVehicle(1L, new HashMap<>(Map.of("make", "Toyota")));
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));
        when(driverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cache.get("driver::1", Driver.class)).thenReturn(null);

        driverService.updateVehicleDetails(1L, Map.of("color", "Red"));

        verify(cache).evict("driver::1");
    }
}
