package com.team10.uber.driver.service;

import com.team10.uber.contracts.dto.DriverRideSummaryDTO;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.dto.DriverEarningsDTO;
import com.team10.uber.driver.messaging.DriverEventPublisher;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for S2-F3: Get Driver Earnings Summary (M3 Feign + §2.10 ownership) */
class DriverServiceS2F3Test {

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
                mock(DriverEventPublisher.class)
        );
        driverService.initObservers();
    }

    private Driver driver(Long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        return d;
    }

    // ── Spec scenario: 5 rides with fares 50/60/70/80/90 ─────────────────────

    @Test
    @DisplayName("Spec — 5 completed rides → totalRides=5, totalEarnings=350, avgFare=70")
    void earnings_happyPath() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(anyString(), eq(DriverEarningsDTO.class))).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenReturn(new DriverRideSummaryDTO(5L, 350.0, 70.0));

        DriverEarningsDTO dto = driverService.getDriverEarningsSummary(1L, START, END, 1L, "DRIVER");

        assertThat(dto.getTotalRides()).isEqualTo(5L);
        assertThat(dto.getTotalEarnings()).isEqualTo(350.0);
        assertThat(dto.getAverageFare()).isEqualTo(70.0);
        assertThat(dto.getDriverId()).isEqualTo(1L);
    }

    // ── Spec scenario: no rides → zero summary ────────────────────────────────

    @Test
    @DisplayName("Spec — Feign returns zeros when no rides in range")
    void earnings_noRidesInRange_returnsZeroes() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(anyString(), eq(DriverEarningsDTO.class))).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenReturn(new DriverRideSummaryDTO(0L, 0.0, 0.0));

        DriverEarningsDTO dto = driverService.getDriverEarningsSummary(1L, START, END, 1L, "DRIVER");

        assertThat(dto.getTotalRides()).isZero();
        assertThat(dto.getTotalEarnings()).isZero();
        assertThat(dto.getAverageFare()).isZero();
    }

    // ── Spec scenario: driver not found → 404 ────────────────────────────────

    @Test
    @DisplayName("Spec — driver not found → 404")
    void earnings_driverNotFound_throws404() {
        when(driverRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(99L, START, END, 99L, "ADMIN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(rideServiceClient);
    }

    // ── startDate > endDate → 400 ─────────────────────────────────────────────

    @Test
    @DisplayName("startDate after endDate → 400")
    void earnings_invalidDateRange_throws400() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));

        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(1L, END, START, 1L, "DRIVER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(rideServiceClient);
    }

    // ── Feign 404 → empty summary (not 404 to caller) ────────────────────────

    @Test
    @DisplayName("Feign 404 → returns zero summary (soft fail)")
    void earnings_feign404_returnsZeroes() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(anyString(), eq(DriverEarningsDTO.class))).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenThrow(mock(FeignException.NotFound.class));

        DriverEarningsDTO dto = driverService.getDriverEarningsSummary(1L, START, END, 1L, "DRIVER");

        assertThat(dto.getTotalRides()).isZero();
    }

    // ── Feign 5xx → 503 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Feign 5xx → 503 Service Unavailable")
    void earnings_feign5xx_throws503() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(anyString(), eq(DriverEarningsDTO.class))).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(1L, "2026-03-01", "2026-03-31"))
                .thenThrow(mock(FeignException.InternalServerError.class));

        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(1L, START, END, 1L, "DRIVER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    // ── M3 §2.10 ownership checks ─────────────────────────────────────────────

    @Test
    @DisplayName("§2.10 — ADMIN bypasses ownership check")
    void ownership_adminBypassesCheck() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(anyString(), eq(DriverEarningsDTO.class))).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(anyLong(), any(), any()))
                .thenReturn(new DriverRideSummaryDTO(0L, 0.0, 0.0));

        assertThatCode(() -> driverService.getDriverEarningsSummary(1L, START, END, 99L, "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§2.10 — driver viewing own earnings passes")
    void ownership_driverViewsOwn_passes() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(anyString(), eq(DriverEarningsDTO.class))).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(anyLong(), any(), any()))
                .thenReturn(new DriverRideSummaryDTO(0L, 0.0, 0.0));

        assertThatCode(() -> driverService.getDriverEarningsSummary(1L, START, END, 1L, "DRIVER"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("§2.10 — mismatch + non-ADMIN → 403")
    void ownership_mismatchRider_throws403() {
        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(1L, START, END, 2L, "RIDER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(driverRepository);
    }
}
