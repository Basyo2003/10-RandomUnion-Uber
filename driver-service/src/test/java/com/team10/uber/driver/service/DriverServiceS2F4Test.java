package com.team10.uber.driver.service;

import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.messaging.DriverEventPublisher;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceS2F4Test {

    @Mock DriverRepository driverRepository;
    @Mock DriverDocumentRepository driverDocumentRepository;
    @Mock MongoEventLogger mongoEventLogger;
    @Mock RedisCacheService cache;
    @Mock ElasticsearchOperations elasticsearchOperations;
    @Mock RideServiceClient rideServiceClient;
    @Mock DriverEventPublisher driverEventPublisher;
    @InjectMocks DriverService driverService;

    private Driver busyDriver;
    private Driver availableDriver;

    @BeforeEach
    void setUp() {
        busyDriver = new Driver();
        busyDriver.setId(1L);
        busyDriver.setStatus(DriverStatus.BUSY);

        availableDriver = new Driver();
        availableDriver.setId(2L);
        availableDriver.setStatus(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Scenario 1 — 400 when active-count > 0 and target is OFFLINE")
    void goOffline_withActiveRides_throws400() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(busyDriver));
        when(rideServiceClient.getDriverActiveRideCount(1L)).thenReturn(1);

        assertThatThrownBy(() -> driverService.updateDriverAvailability(1L, DriverStatus.OFFLINE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(driverRepository, never()).save(any());
        verify(driverEventPublisher, never()).publishStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("Scenario 2 — 200 when active-count = 0 and target is OFFLINE")
    void goOffline_noActiveRides_updatesAndPublishes() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(busyDriver));
        when(rideServiceClient.getDriverActiveRideCount(1L)).thenReturn(0);
        when(driverRepository.save(any())).thenReturn(busyDriver);

        driverService.updateDriverAvailability(1L, DriverStatus.OFFLINE);

        verify(driverRepository).save(argThat(d -> d.getStatus() == DriverStatus.OFFLINE));
        verify(driverEventPublisher).publishStatusChanged(1L, "BUSY", "OFFLINE");
    }

    @Test
    @DisplayName("Scenario 3 — AVAILABLE transition skips Feign entirely")
    void goAvailable_doesNotCallFeign() {
        when(driverRepository.findById(2L)).thenReturn(Optional.of(availableDriver));
        when(driverRepository.save(any())).thenReturn(availableDriver);

        driverService.updateDriverAvailability(2L, DriverStatus.AVAILABLE);

        verify(rideServiceClient, never()).getDriverActiveRideCount(any());
        verify(driverEventPublisher).publishStatusChanged(eq(2L), any(), eq("AVAILABLE"));
    }

    @Test
    @DisplayName("Scenario 4 — 404 when driver not found")
    void driverNotFound_throws404() {
        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.updateDriverAvailability(999L, DriverStatus.OFFLINE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(rideServiceClient, never()).getDriverActiveRideCount(any());
    }

    @Test
    @DisplayName("Scenario 6 — 503 when ride-service is unavailable")
    void feignServerError_throws503() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(busyDriver));
        when(rideServiceClient.getDriverActiveRideCount(1L))
                .thenThrow(FeignException.InternalServerError.class);

        assertThatThrownBy(() -> driverService.updateDriverAvailability(1L, DriverStatus.OFFLINE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");

        verify(driverRepository, never()).save(any());
        verify(driverEventPublisher, never()).publishStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("oldStatus is captured before mutation and passed to event payload")
    void oldStatusCapturedBeforeMutation() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(busyDriver));
        when(rideServiceClient.getDriverActiveRideCount(1L)).thenReturn(0);
        when(driverRepository.save(any())).thenReturn(busyDriver);

        driverService.updateDriverAvailability(1L, DriverStatus.OFFLINE);

        // oldStatus must be "BUSY" (what it was before), not "OFFLINE" (what it became)
        verify(driverEventPublisher).publishStatusChanged(1L, "BUSY", "OFFLINE");
    }

    @Test
    @DisplayName("publishStatusChanged is called AFTER save (publish-after-commit §2.11)")
    void publishCalledAfterSave() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(busyDriver));
        when(rideServiceClient.getDriverActiveRideCount(1L)).thenReturn(0);
        when(driverRepository.save(any())).thenReturn(busyDriver);

        var inOrder = inOrder(driverRepository, driverEventPublisher);

        driverService.updateDriverAvailability(1L, DriverStatus.OFFLINE);

        inOrder.verify(driverRepository).save(any());
        inOrder.verify(driverEventPublisher).publishStatusChanged(any(), any(), any());
    }

    @Test
    @DisplayName("BUSY transition also skips Feign and publishes event")
    void goBusy_doesNotCallFeign_publishesEvent() {
        when(driverRepository.findById(2L)).thenReturn(Optional.of(availableDriver));
        when(driverRepository.save(any())).thenReturn(availableDriver);

        driverService.updateDriverAvailability(2L, DriverStatus.BUSY);

        verify(rideServiceClient, never()).getDriverActiveRideCount(any());
        verify(driverEventPublisher).publishStatusChanged(eq(2L), eq("AVAILABLE"), eq("BUSY"));
    }

    @Test
    @DisplayName("evictDriverCaches is called after save")
    void cacheEvictedAfterSave() {
        when(driverRepository.findById(2L)).thenReturn(Optional.of(availableDriver));
        when(driverRepository.save(any())).thenReturn(availableDriver);

        driverService.updateDriverAvailability(2L, DriverStatus.AVAILABLE);

        verify(cache).evict("driver::2");
    }
}
