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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for S2-F1: Search Drivers by Status and Rating Range */
class DriverServiceS2F1Test {

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

    private Driver driver(Long id, double rating, DriverStatus status) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setRating(rating);
        d.setStatus(status);
        return d;
    }

    // ── Spec scenario (b): 2 AVAILABLE drivers in 4.0–5.0 range, ordered desc ─

    @Test
    @DisplayName("Spec scenario b — AVAILABLE + 4.0–5.0 returns 2 drivers ordered 4.9 first")
    void search_statusAndRating_returnsOrderedResults() {
        Driver d1 = driver(1L, 4.5, DriverStatus.AVAILABLE);
        Driver d3 = driver(3L, 4.9, DriverStatus.AVAILABLE);
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.searchDrivers("AVAILABLE", 4.0, 5.0))
                .thenReturn(List.of(d3, d1)); // repo returns already ordered

        List<Driver> result = driverService.searchDrivers(DriverStatus.AVAILABLE, 4.0, 5.0);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRating()).isEqualTo(4.9);
        assertThat(result.get(1).getRating()).isEqualTo(4.5);
    }

    // ── Spec scenario (c): no status filter ──────────────────────────────────

    @Test
    @DisplayName("Spec scenario c — no status filter returns matching driver")
    void search_noStatus_returnsMatchingDriver() {
        Driver d2 = driver(2L, 3.8, DriverStatus.BUSY);
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.searchDrivers(null, 3.0, 4.0)).thenReturn(List.of(d2));

        List<Driver> result = driverService.searchDrivers(null, 3.0, 4.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRating()).isEqualTo(3.8);
    }

    // ── Spec scenario (d): minRating > maxRating → 400 ───────────────────────

    @Test
    @DisplayName("Spec scenario d — minRating > maxRating throws 400")
    void search_invalidRange_throws400() {
        assertThatThrownBy(() -> driverService.searchDrivers(null, 5.0, 3.0))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(driverRepository);
    }

    // ── Additional validation scenarios ──────────────────────────────────────

    @Test
    @DisplayName("minRating < 0 throws 400")
    void search_negativMinRating_throws400() {
        assertThatThrownBy(() -> driverService.searchDrivers(null, -1.0, 5.0))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("maxRating > 5 throws 400")
    void search_maxRatingAbove5_throws400() {
        assertThatThrownBy(() -> driverService.searchDrivers(null, 1.0, 6.0))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── Cache tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cache hit — repository never called")
    void search_cacheHit_skipsRepository() {
        List<Driver> cached = List.of(driver(1L, 4.5, DriverStatus.AVAILABLE));
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(cached);

        List<Driver> result = driverService.searchDrivers(DriverStatus.AVAILABLE, 4.0, 5.0);

        assertThat(result).isSameAs(cached);
        verify(driverRepository, never()).searchDrivers(any(), any(), any());
    }

    @Test
    @DisplayName("Result stored in cache with 5-minute TTL")
    void search_resultStoredInCache() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.searchDrivers(any(), any(), any())).thenReturn(List.of());

        driverService.searchDrivers(DriverStatus.AVAILABLE, 4.0, 5.0);

        verify(cache).put(anyString(), anyList(), eq(5 * 60L));
    }

    @Test
    @DisplayName("No filters — all drivers returned")
    void search_noFilters_returnsAll() {
        List<Driver> all = List.of(driver(1L, 4.9, DriverStatus.AVAILABLE),
                driver(2L, 3.8, DriverStatus.BUSY));
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.searchDrivers(null, null, null)).thenReturn(all);

        List<Driver> result = driverService.searchDrivers(null, null, null);

        assertThat(result).hasSize(2);
    }
}
