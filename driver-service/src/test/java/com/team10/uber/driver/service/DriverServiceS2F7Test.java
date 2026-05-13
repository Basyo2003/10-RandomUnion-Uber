package com.team10.uber.driver.service;

import com.team10.uber.contracts.dto.RideDTO;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
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
class DriverServiceS2F7Test {

    @Mock DriverRepository driverRepository;
    @Mock DriverDocumentRepository driverDocumentRepository;
    @Mock MongoEventLogger mongoEventLogger;
    @Mock RedisCacheService cache;
    @Mock ElasticsearchOperations elasticsearchOperations;
    @Mock RideServiceClient rideServiceClient;
    @Mock DriverEventPublisher driverEventPublisher;
    @InjectMocks DriverService driverService;

    private Driver driver1;
    // RideDTO(id, userId, driverId, status, fare, requestedAt, completedAt, metadata)
    private RideDTO completedRide;
    private RideDTO wrongDriverRide;
    private RideDTO inProgressRide;

    @BeforeEach
    void setUp() {
        driver1 = new Driver();
        driver1.setId(1L);
        driver1.setRating(0.0);
        driver1.setTotalRatings(0);

        completedRide   = new RideDTO(10L, 7L, 1L, "COMPLETED", null, null, null, null);
        wrongDriverRide = new RideDTO(10L, 7L, 2L, "COMPLETED", null, null, null, null);
        inProgressRide  = new RideDTO(12L, 7L, 1L, "IN_PROGRESS", null, null, null, null);
    }

    @Test
    @DisplayName("Scenario 1 — first rating: 200, rating=5.0, totalRatings=1, event published")
    void firstRating_updatesCorrectly() {
        Driver updatedDriver = new Driver();
        updatedDriver.setId(1L);
        updatedDriver.setRating(5.0);
        updatedDriver.setTotalRatings(1);

        when(driverRepository.findById(1L))
                .thenReturn(Optional.of(driver1))
                .thenReturn(Optional.of(updatedDriver));
        when(rideServiceClient.getRide(10L)).thenReturn(completedRide);
        when(driverRepository.applyRating(1L, 5.0)).thenReturn(1);

        Driver result = driverService.rateDriver(1L, 10L, 5.0);

        assertThat(result.getRating()).isEqualTo(5.0);
        assertThat(result.getTotalRatings()).isEqualTo(1);
        verify(driverRepository).applyRating(1L, 5.0);
        verify(driverEventPublisher).publishDriverRated(1L, 10L, 5.0, 7L);
    }

    @Test
    @DisplayName("Scenario 3 — ride not found: Feign 404 → throw 404")
    void rideNotFound_throws404() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(rideServiceClient.getRide(99L)).thenThrow(FeignException.NotFound.class);

        assertThatThrownBy(() -> driverService.rateDriver(1L, 99L, 4.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(driverRepository, never()).applyRating(any(), any());
        verify(driverEventPublisher, never()).publishDriverRated(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Scenario 4 — driverId mismatch: 400")
    void driverIdMismatch_throws400() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(rideServiceClient.getRide(10L)).thenReturn(wrongDriverRide);

        assertThatThrownBy(() -> driverService.rateDriver(1L, 10L, 4.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(driverRepository, never()).applyRating(any(), any());
    }

    @Test
    @DisplayName("Scenario 5 — rating out of range: 400 BEFORE Feign call")
    void ratingOutOfRange_throws400BeforeFeign() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));

        assertThatThrownBy(() -> driverService.rateDriver(1L, 10L, 6.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(rideServiceClient, never()).getRide(any());
    }

    @Test
    @DisplayName("Scenario 5b — rating = 0 is out of range: 400")
    void ratingZero_throws400() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));

        assertThatThrownBy(() -> driverService.rateDriver(1L, 10L, 0.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(rideServiceClient, never()).getRide(any());
    }

    @Test
    @DisplayName("Scenario 6 — ride not COMPLETED: 400")
    void rideNotCompleted_throws400() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(rideServiceClient.getRide(12L)).thenReturn(inProgressRide);

        assertThatThrownBy(() -> driverService.rateDriver(1L, 12L, 4.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(driverRepository, never()).applyRating(any(), any());
    }

    @Test
    @DisplayName("Scenario 7 — driver not found: 404")
    void driverNotFound_throws404() {
        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.rateDriver(999L, 10L, 4.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(rideServiceClient, never()).getRide(any());
    }

    @Test
    @DisplayName("Scenario 8 — ride-service 5xx: throw 503")
    void feignServerError_throws503() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(rideServiceClient.getRide(10L)).thenThrow(FeignException.InternalServerError.class);

        assertThatThrownBy(() -> driverService.rateDriver(1L, 10L, 4.0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");

        verify(driverRepository, never()).applyRating(any(), any());
        verify(driverEventPublisher, never()).publishDriverRated(any(), any(), any(), any());
    }

    @Test
    @DisplayName("applyRating called with correct driverId and rating")
    void applyRating_calledWithCorrectArgs() {
        Driver updated = new Driver();
        updated.setId(1L);
        updated.setRating(4.5);
        updated.setTotalRatings(1);

        when(driverRepository.findById(1L))
                .thenReturn(Optional.of(driver1))
                .thenReturn(Optional.of(updated));
        when(rideServiceClient.getRide(10L)).thenReturn(completedRide);
        when(driverRepository.applyRating(1L, 4.5)).thenReturn(1);

        driverService.rateDriver(1L, 10L, 4.5);

        verify(driverRepository).applyRating(1L, 4.5);
    }

    @Test
    @DisplayName("publishDriverRated called AFTER applyRating (publish-after-commit §2.11)")
    void publishCalledAfterApplyRating() {
        Driver updated = new Driver();
        updated.setId(1L);
        updated.setRating(5.0);
        updated.setTotalRatings(1);

        when(driverRepository.findById(1L))
                .thenReturn(Optional.of(driver1))
                .thenReturn(Optional.of(updated));
        when(rideServiceClient.getRide(10L)).thenReturn(completedRide);
        when(driverRepository.applyRating(1L, 5.0)).thenReturn(1);

        var inOrder = inOrder(driverRepository, driverEventPublisher);

        driverService.rateDriver(1L, 10L, 5.0);

        inOrder.verify(driverRepository).applyRating(1L, 5.0);
        inOrder.verify(driverEventPublisher).publishDriverRated(any(), any(), any(), any());
    }

    @Test
    @DisplayName("userId in event payload comes from ride.userId(), not from caller")
    void eventPayload_usesRideUserId() {
        Driver updated = new Driver();
        updated.setId(1L);
        updated.setRating(5.0);
        updated.setTotalRatings(1);

        when(driverRepository.findById(1L))
                .thenReturn(Optional.of(driver1))
                .thenReturn(Optional.of(updated));
        when(rideServiceClient.getRide(10L)).thenReturn(completedRide); // userId=7
        when(driverRepository.applyRating(1L, 5.0)).thenReturn(1);

        driverService.rateDriver(1L, 10L, 5.0);

        verify(driverEventPublisher).publishDriverRated(1L, 10L, 5.0, 7L);
    }

    @Test
    @DisplayName("read-modify-write NOT used — driverRepository.save never called")
    void readModifyWriteNotUsed_saveNeverCalled() {
        Driver updated = new Driver();
        updated.setId(1L);

        when(driverRepository.findById(1L))
                .thenReturn(Optional.of(driver1))
                .thenReturn(Optional.of(updated));
        when(rideServiceClient.getRide(10L)).thenReturn(completedRide);
        when(driverRepository.applyRating(1L, 5.0)).thenReturn(1);

        driverService.rateDriver(1L, 10L, 5.0);

        verify(driverRepository, never()).save(any());
    }
}
