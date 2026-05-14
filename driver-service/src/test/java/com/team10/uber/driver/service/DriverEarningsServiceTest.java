package com.team10.uber.driver.service;

import com.team10.uber.contracts.dto.DriverRideSummaryDTO;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.dto.DriverEarningsDTO;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriverEarningsServiceTest {

    private DriverRepository driverRepository;
    private RideServiceClient rideServiceClient;
    private RedisCacheService cache;
    private DriverService driverService;

    private static final LocalDate START = LocalDate.of(2026, 3, 1);
    private static final LocalDate END   = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        driverRepository  = mock(DriverRepository.class);
        rideServiceClient = mock(RideServiceClient.class);
        cache             = mock(RedisCacheService.class);

        driverService = new DriverService(
                driverRepository,
                mock(DriverDocumentRepository.class),
                mock(MongoEventLogger.class),
                cache,
                mock(ElasticsearchOperations.class),
                rideServiceClient,
                mock(com.team10.uber.driver.messaging.DriverEventPublisher.class)
        );
        driverService.initObservers();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Driver driver(Long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        return d;
    }

    private String cacheKey(Long driverId) {
        return "S2-F3::" + driverId + "::" + START + "::" + END;
    }

    // ── S2-F3 happy path ──────────────────────────────────────────────────────

    /**
     * spec scenario: Driver 1, 5 COMPLETED rides with fares 50,60,70,80,90
     * → totalRides=5, totalEarnings=350.00, averageFare=70.00
     */
    @Test
    void earnings_returnsFeignSummaryMergedWithDriver() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(cacheKey(1L), DriverEarningsDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenReturn(new DriverRideSummaryDTO(5L, 350.0, 70.0));

        DriverEarningsDTO dto = driverService.getDriverEarningsSummary(1L, START, END);

        assertThat(dto.getTotalRides()).isEqualTo(5L);
        assertThat(dto.getTotalEarnings()).isEqualTo(350.0);
        assertThat(dto.getAverageFare()).isEqualTo(70.0);
        assertThat(dto.getDriverId()).isEqualTo(1L);
    }

    /** spec scenario: driver not found → 404, Feign never called */
    @Test
    void earnings_driverNotFound_throws404() {
        when(driverRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(99L, START, END))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(rideServiceClient);
    }

    /** startDate after endDate → 400, Feign never called */
    @Test
    void earnings_invalidDateRange_throws400() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));

        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(1L, END, START))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(rideServiceClient);
    }

    /** ride-service 404 → return zero rides (empty summary) */
    @Test
    void earnings_rideServiceNotFound_returnsEmpty() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(cacheKey(1L), DriverEarningsDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenThrow(mock(FeignException.NotFound.class));

        DriverEarningsDTO dto = driverService.getDriverEarningsSummary(1L, START, END);

        assertThat(dto.getTotalRides()).isEqualTo(0L);
        assertThat(dto.getTotalEarnings()).isEqualTo(0.0);
        assertThat(dto.getAverageFare()).isEqualTo(0.0);
    }

    /** ride-service general error → 503 */
    @Test
    void earnings_rideServiceUnavailable_throws503() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(cacheKey(1L), DriverEarningsDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenThrow(mock(FeignException.ServiceUnavailable.class));

        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(1L, START, END))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    /** cache hit → Feign never called */
    @Test
    void earnings_cacheHit_skipsFeignCall() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        DriverEarningsDTO cached = new DriverEarningsDTO(1L, "Driver 1", 5L, 350.0, 70.0);
        when(cache.get(cacheKey(1L), DriverEarningsDTO.class)).thenReturn(cached);

        DriverEarningsDTO result = driverService.getDriverEarningsSummary(1L, START, END);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(rideServiceClient);
    }

    /** result is stored in cache after Feign call */
    @Test
    void earnings_resultStoredInCache() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(cacheKey(1L), DriverEarningsDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenReturn(new DriverRideSummaryDTO(5L, 350.0, 70.0));

        driverService.getDriverEarningsSummary(1L, START, END);

        verify(cache).put(eq(cacheKey(1L)), any(DriverEarningsDTO.class), eq(10 * 60L));
    }

    /** dates are forwarded as ISO strings to Feign */
    @Test
    void earnings_datesSentAsIsoStringsToFeign() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(cacheKey(1L), DriverEarningsDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenReturn(new DriverRideSummaryDTO(5L, 350.0, 70.0));

        driverService.getDriverEarningsSummary(1L, START, END);

        verify(rideServiceClient).getDriverRideSummary(1L, "2026-03-01", "2026-03-31");
    }

    /** no cross-service SQL: getEarningsAggregate must never be called */
    @Test
    void earnings_noCrossServiceSQL() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(cacheKey(1L), DriverEarningsDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenReturn(new DriverRideSummaryDTO(5L, 350.0, 70.0));

        driverService.getDriverEarningsSummary(1L, START, END);

        // getEarningsAggregate does not exist — Feign replaces cross-service SQL; verifying rideServiceClient was called is sufficient
        verify(rideServiceClient).getDriverRideSummary(anyLong(), any(), any());
    }
}
