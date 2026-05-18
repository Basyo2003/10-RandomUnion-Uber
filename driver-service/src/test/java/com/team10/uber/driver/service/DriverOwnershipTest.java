package com.team10.uber.driver.service;

import com.team10.uber.contracts.dto.DriverRideSummaryDTO;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.dto.DriverDashboardDTO;
import com.team10.uber.driver.dto.DriverEarningsDTO;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M3 §2.10 ownership rule tests for S2-F3 (earnings) and S2-F12 (dashboard).
 * Rule: path-param {driverId} must equal X-User-Id, OR X-User-Role == ADMIN; else → 403.
 */
class DriverOwnershipTest {

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
        d.setRating(4.5);
        d.setTotalRatings(10);
        return d;
    }

    // ════════════════════════════════════════════════════════════════════════
    // S2-F3 earnings — ownership scenarios
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Earnings — ADMIN with mismatched id passes (ADMIN bypasses ownership)")
    void earnings_adminBypasses() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(anyString(), eq(DriverEarningsDTO.class))).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(anyLong(), any(), any()))
                .thenReturn(new DriverRideSummaryDTO(0L, 0.0, 0.0));

        assertThatCode(() -> driverService.getDriverEarningsSummary(1L, START, END, 99L, "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Earnings — driver viewing own resource (id match) passes")
    void earnings_idMatch_passes() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get(anyString(), eq(DriverEarningsDTO.class))).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(anyLong(), any(), any()))
                .thenReturn(new DriverRideSummaryDTO(0L, 0.0, 0.0));

        assertThatCode(() -> driverService.getDriverEarningsSummary(1L, START, END, 1L, "DRIVER"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Earnings — id mismatch + RIDER role → 403")
    void earnings_mismatchRider_throws403() {
        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(1L, START, END, 2L, "RIDER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(driverRepository);
    }

    @Test
    @DisplayName("Earnings — id mismatch + DRIVER role → 403")
    void earnings_mismatchDriver_throws403() {
        assertThatThrownBy(() -> driverService.getDriverEarningsSummary(1L, START, END, 2L, "DRIVER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ════════════════════════════════════════════════════════════════════════
    // S2-F12 dashboard — ownership scenarios
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Dashboard — ADMIN with mismatched id passes")
    void dashboard_adminBypasses() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get("S2-F12::1", DriverDashboardDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(anyLong(), any(), any()))
                .thenReturn(new DriverRideSummaryDTO(0L, 0.0, 0.0));

        assertThatCode(() -> driverService.getDriverDashboard(1L, 99L, "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Dashboard — driver viewing own resource (id match) passes")
    void dashboard_idMatch_passes() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(cache.get("S2-F12::1", DriverDashboardDTO.class)).thenReturn(null);
        when(rideServiceClient.getDriverRideSummary(anyLong(), any(), any()))
                .thenReturn(new DriverRideSummaryDTO(0L, 0.0, 0.0));

        assertThatCode(() -> driverService.getDriverDashboard(1L, 1L, "DRIVER"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Dashboard — id mismatch + RIDER role → 403")
    void dashboard_mismatchRider_throws403() {
        assertThatThrownBy(() -> driverService.getDriverDashboard(1L, 2L, "RIDER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(driverRepository);
    }

    @Test
    @DisplayName("Dashboard — id mismatch + DRIVER role → 403")
    void dashboard_mismatchDriver_throws403() {
        assertThatThrownBy(() -> driverService.getDriverDashboard(1L, 2L, "DRIVER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Dashboard — ADMIN gets 404 if driver doesn't exist (ownership doesn't mask 404)")
    void dashboard_adminPassesOwnership_404IfDriverMissing() {
        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getDriverDashboard(999L, 999L, "ADMIN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
