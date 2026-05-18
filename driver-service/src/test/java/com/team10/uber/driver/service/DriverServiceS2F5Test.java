package com.team10.uber.driver.service;

import com.fasterxml.jackson.core.type.TypeReference;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for S2-F5: Filter Drivers by Vehicle Type (JSONB) */
class DriverServiceS2F5Test {

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

    private Driver driver(Long id, String vehicleType, DriverStatus status) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setStatus(status);
        d.setVehicleDetails(Map.of("vehicleType", vehicleType));
        return d;
    }

    // ── Spec scenario (b): SEDAN + AVAILABLE → 1 driver ─────────────────────

    @Test
    @DisplayName("Spec scenario b — SEDAN + AVAILABLE returns 1 driver")
    void filter_sedanAvailable_returns1() {
        Driver d1 = driver(1L, "SEDAN", DriverStatus.AVAILABLE);
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findByVehicleType("SEDAN", "AVAILABLE")).thenReturn(List.of(d1));

        List<Driver> result = driverService.filterByVehicleType("SEDAN", "AVAILABLE");

        assertThat(result).hasSize(1);
    }

    // ── Spec scenario (c): SEDAN only → 2 drivers ────────────────────────────

    @Test
    @DisplayName("Spec scenario c — SEDAN (no status) returns all SEDAN drivers")
    void filter_sedanNoStatus_returns2() {
        Driver d1 = driver(1L, "SEDAN", DriverStatus.AVAILABLE);
        Driver d3 = driver(3L, "SEDAN", DriverStatus.OFFLINE);
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findByVehicleType("SEDAN", null)).thenReturn(List.of(d1, d3));

        List<Driver> result = driverService.filterByVehicleType("SEDAN", null);

        assertThat(result).hasSize(2);
    }

    // ── Spec scenario (d): unknown type → empty list (M1 §9.2.5 scenario d) ────

    @Test
    @DisplayName("Spec scenario d — unknown vehicle type TRUCK returns empty list")
    void filter_unknownType_returnsEmpty() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findByVehicleType("TRUCK", null)).thenReturn(List.of());

        List<Driver> result = driverService.filterByVehicleType("TRUCK", null);

        assertThat(result).isEmpty();
    }

    // ── Additional validation ─────────────────────────────────────────────────

    @Test
    @DisplayName("Blank type throws 400")
    void filter_blankType_throws400() {
        assertThatThrownBy(() -> driverService.filterByVehicleType("  ", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Null type throws 400")
    void filter_nullType_throws400() {
        assertThatThrownBy(() -> driverService.filterByVehicleType(null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Invalid status string throws 400")
    void filter_invalidStatus_throws400() {
        assertThatThrownBy(() -> driverService.filterByVehicleType("SEDAN", "UNKNOWN_STATUS"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("SUV type returns SUV drivers")
    void filter_suvType_returnsSuvDrivers() {
        Driver d2 = driver(2L, "SUV", DriverStatus.AVAILABLE);
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findByVehicleType("SUV", null)).thenReturn(List.of(d2));

        List<Driver> result = driverService.filterByVehicleType("SUV", null);

        assertThat(result).hasSize(1);
    }

    // ── Cache tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cache hit — repository never called")
    void filter_cacheHit_skipsRepository() {
        List<Driver> cached = List.of(driver(1L, "SEDAN", DriverStatus.AVAILABLE));
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(cached);

        List<Driver> result = driverService.filterByVehicleType("SEDAN", null);

        assertThat(result).isSameAs(cached);
        verify(driverRepository, never()).findByVehicleType(any(), any());
    }

    @Test
    @DisplayName("Result stored in cache with 5-minute TTL")
    void filter_resultStoredInCache() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findByVehicleType(any(), any())).thenReturn(List.of());

        driverService.filterByVehicleType("SEDAN", null);

        verify(cache).put(anyString(), anyList(), eq(5 * 60L));
    }
}
