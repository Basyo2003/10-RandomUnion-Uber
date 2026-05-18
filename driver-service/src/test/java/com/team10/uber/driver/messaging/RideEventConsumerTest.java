package com.team10.uber.driver.messaging;

import com.team10.uber.contracts.events.RideCancelledEvent;
import com.team10.uber.contracts.events.RideCompletedEvent;
import com.team10.uber.contracts.events.RidePlacedEvent;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RideEventConsumerTest {

    private DriverRepository driverRepository;
    private RedisCacheService cache;
    private RideEventConsumer consumer;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        cache = mock(RedisCacheService.class);
        consumer = new RideEventConsumer(driverRepository, new com.fasterxml.jackson.databind.ObjectMapper(), cache);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Driver driver(Long id, DriverStatus status, int totalRatings) {
        Driver d = new Driver();
        d.setId(id);
        d.setStatus(status);
        d.setTotalRatings(totalRatings);
        return d;
    }

    // ── ride.placed ───────────────────────────────────────────────────────────

    /** spec: ride.placed sets driver to BUSY */
    @Test
    void ridePlaced_setsDriverToBusy() {
        Driver d = driver(1L, DriverStatus.AVAILABLE, 0);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        consumer.handleRidePlaced(new RidePlacedEvent(10L, 7L, 1L));

        assertThat(d.getStatus()).isEqualTo(DriverStatus.BUSY);
        verify(driverRepository).save(d);
    }

    /** spec: ride.placed is idempotent — if driver already BUSY, no-op (no save, no throw) */
    @Test
    void ridePlaced_alreadyBusy_isIdempotentNoOp() {
        Driver d = driver(1L, DriverStatus.BUSY, 0);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        consumer.handleRidePlaced(new RidePlacedEvent(10L, 7L, 1L));

        assertThat(d.getStatus()).isEqualTo(DriverStatus.BUSY);
        verify(driverRepository, never()).save(any());
    }

    /** driver not found — no exception thrown (log + skip) */
    @Test
    void ridePlaced_driverNotFound_doesNotThrow() {
        when(driverRepository.findById(99L)).thenReturn(Optional.empty());

        consumer.handleRidePlaced(new RidePlacedEvent(10L, 7L, 99L));

        verify(driverRepository, never()).save(any());
    }

    // ── ride.completed ────────────────────────────────────────────────────────

    /** spec: ride.completed sets driver to AVAILABLE and increments stats */
    @Test
    void rideCompleted_setsAvailableAndIncrementsStats() {
        Driver d = driver(1L, DriverStatus.BUSY, 5);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        consumer.handleRideCompleted(new RideCompletedEvent(10L, 7L, 1L, 50.0));

        assertThat(d.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(d.getTotalRatings()).isEqualTo(6);
        verify(driverRepository).save(d);
    }

    /** driver not found — no exception thrown */
    @Test
    void rideCompleted_driverNotFound_doesNotThrow() {
        when(driverRepository.findById(99L)).thenReturn(Optional.empty());

        consumer.handleRideCompleted(new RideCompletedEvent(10L, 7L, 99L, 50.0));

        verify(driverRepository, never()).save(any());
    }

    // ── ride.cancelled ────────────────────────────────────────────────────────

    /** spec: ride.cancelled sets driver to AVAILABLE and reverses stats */
    @Test
    void rideCancelled_setsAvailableAndReverseStats() {
        Driver d = driver(1L, DriverStatus.BUSY, 3);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        consumer.handleRideCancelled(new RideCancelledEvent(10L, 7L, 1L, "rider cancelled"));

        assertThat(d.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(d.getTotalRatings()).isEqualTo(2);
        verify(driverRepository).save(d);
    }

    /** stats reversal floor: totalRatings cannot go below 0 */
    @Test
    void rideCancelled_statsAtZero_doesNotDecrementBelowZero() {
        Driver d = driver(1L, DriverStatus.BUSY, 0);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        consumer.handleRideCancelled(new RideCancelledEvent(10L, 7L, 1L, "rider cancelled"));

        assertThat(d.getTotalRatings()).isEqualTo(0);
        verify(driverRepository).save(d);
    }

    /** driver not found — no exception thrown */
    @Test
    void rideCancelled_driverNotFound_doesNotThrow() {
        when(driverRepository.findById(99L)).thenReturn(Optional.empty());

        consumer.handleRideCancelled(new RideCancelledEvent(10L, 7L, 99L, "rider cancelled"));

        verify(driverRepository, never()).save(any());
    }
}
