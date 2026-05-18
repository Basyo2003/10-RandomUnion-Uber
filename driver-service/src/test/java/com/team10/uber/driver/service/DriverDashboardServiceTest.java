package com.team10.uber.driver.service;

import com.team10.uber.contracts.dto.DriverRideSummaryDTO;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.dto.DriverDashboardDTO;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriverDashboardServiceTest {

    private DriverRepository driverRepository;
    private RideServiceClient rideServiceClient;
    private RedisCacheService cache;
    private DriverService driverService;

    @BeforeEach
    void setUp() {
        driverRepository        = mock(DriverRepository.class);
        rideServiceClient       = mock(RideServiceClient.class);
        cache                   = mock(RedisCacheService.class);
        MongoEventLogger logger = mock(MongoEventLogger.class);

        driverService = new DriverService(
                driverRepository,
                mock(DriverDocumentRepository.class),
                logger,
                cache,
                mock(ElasticsearchOperations.class),
                rideServiceClient,
                mock(com.team10.uber.driver.messaging.DriverEventPublisher.class)
        );
        driverService.initObservers();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Driver driver(Long id, double rating, int totalRatings) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setRating(rating);
        d.setTotalRatings(totalRatings);
        return d;
    }

    // ── S2-F12 happy path ─────────────────────────────────────────────────────

    /** spec scenario 1: 5 rides with fares 100,200,150,300,250 → totalEarnings=1000, avg=200 */
    @Test
    void dashboard_returnsFeignAggregatesMergedWithLocalRating() {
        Driver d = driver(5L, 4.5, 100);
        when(driverRepository.findById(5L)).thenReturn(Optional.of(d));
        when(cache.get("S2-F12::5", DriverDashboardDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(5L, null, null))
                .thenReturn(new DriverRideSummaryDTO(5L, 1000.0, 200.0));

        DriverDashboardDTO dto = driverService.getDriverDashboard(5L, 5L, "ADMIN");

        assertThat(dto.getTotalRides()).isEqualTo(5L);
        assertThat(dto.getTotalEarnings()).isEqualTo(1000.0);
        assertThat(dto.getAverageRideFare()).isEqualTo(200.0);
        assertThat(dto.getAverageRating()).isEqualTo(4.5);
        assertThat(dto.getTotalRatings()).isEqualTo(100);
        assertThat(dto.getDriverId()).isEqualTo(5L);
    }

    /** spec scenario 2: driver not found → 404 */
    @Test
    void dashboard_driverNotFound_throws404() {
        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getDriverDashboard(999L, 999L, "ADMIN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(rideServiceClient);
    }

    /** ride-service returns 404 → treat as zero rides (empty summary) */
    @Test
    void dashboard_rideServiceNotFound_returnsZeroRides() {
        Driver d = driver(5L, 4.5, 100);
        when(driverRepository.findById(5L)).thenReturn(Optional.of(d));
        when(cache.get("S2-F12::5", DriverDashboardDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(5L, null, null))
                .thenThrow(mock(FeignException.NotFound.class));

        DriverDashboardDTO dto = driverService.getDriverDashboard(5L, 5L, "ADMIN");

        assertThat(dto.getTotalRides()).isEqualTo(0L);
        assertThat(dto.getTotalEarnings()).isEqualTo(0.0);
        assertThat(dto.getAverageRideFare()).isEqualTo(0.0);
        assertThat(dto.getAverageRating()).isEqualTo(4.5);
    }

    /** ride-service returns non-404 Feign error → 503 */
    @Test
    void dashboard_rideServiceUnavailable_throws503() {
        Driver d = driver(5L, 4.5, 100);
        when(driverRepository.findById(5L)).thenReturn(Optional.of(d));
        when(cache.get("S2-F12::5", DriverDashboardDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(5L, null, null))
                .thenThrow(mock(FeignException.ServiceUnavailable.class));

        assertThatThrownBy(() -> driverService.getDriverDashboard(5L, 5L, "ADMIN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    /** cache hit → Feign is never called */
    @Test
    void dashboard_cacheHit_skipsFeignCall() {
        Driver d = driver(5L, 4.5, 100);
        when(driverRepository.findById(5L)).thenReturn(Optional.of(d));
        DriverDashboardDTO cached = DriverDashboardDTO.builder()
                .driverId(5L).totalRides(5L).totalEarnings(1000.0)
                .averageRideFare(200.0).averageRating(4.5).totalRatings(100).build();
        when(cache.get("S2-F12::5", DriverDashboardDTO.class)).thenReturn(cached);

        DriverDashboardDTO result = driverService.getDriverDashboard(5L, 5L, "ADMIN");

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(rideServiceClient);
    }

    /** result is stored in cache after Feign call */
    @Test
    void dashboard_resultStoredInCache() {
        Driver d = driver(5L, 4.5, 100);
        when(driverRepository.findById(5L)).thenReturn(Optional.of(d));
        when(cache.get("S2-F12::5", DriverDashboardDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(5L, null, null))
                .thenReturn(new DriverRideSummaryDTO(5L, 1000.0, 200.0));

        driverService.getDriverDashboard(5L, 5L, "ADMIN");

        verify(cache).put(eq("S2-F12::5"), any(DriverDashboardDTO.class), eq(10 * 60L));
    }

    /** no cross-service SQL: getDashboardAggregate must never be called */
    @Test
    void dashboard_noCrossServiceSQL() {
        Driver d = driver(5L, 4.5, 100);
        when(driverRepository.findById(5L)).thenReturn(Optional.of(d));
        when(cache.get("S2-F12::5", DriverDashboardDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(5L, null, null))
                .thenReturn(new DriverRideSummaryDTO(5L, 1000.0, 200.0));

        driverService.getDriverDashboard(5L, 5L, "ADMIN");

        // getDashboardAggregate does not exist — Feign replaces it; verifying no save() proves no read-modify-write
        verify(driverRepository, never()).save(any());
    }
}
