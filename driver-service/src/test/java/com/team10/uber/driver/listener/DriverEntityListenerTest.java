package com.team10.uber.driver.listener;

import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.service.DriverIndexService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DriverEntityListenerTest {

    private DriverIndexService mockIndexService;
    private DriverEntityListener listener;

    @BeforeEach
    void setUp() {
        mockIndexService = mock(DriverIndexService.class);
        listener = new DriverEntityListener();
        listener.setDriverIndexService(mockIndexService);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** Simulates the JPA transaction committing, firing all registered afterCommit() callbacks. */
    private void fireAfterCommit() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
    }

    // ── @PostPersist ──────────────────────────────────────────────────────────
    @Test
    void postPersist_callsIndexDriverWithAutoCreateSource() {
        listener.onPostPersist(driverWithId(42L));
        fireAfterCommit();

        verify(mockIndexService).indexDriver(42L, "auto_crud_create");
    }

    @Test
    void postPersist_doesNotCallRemoveFromIndex() {
        listener.onPostPersist(driverWithId(1L));
        fireAfterCommit();

        verify(mockIndexService, never()).removeFromIndex(anyLong());
    }

    // ── @PostUpdate ───────────────────────────────────────────────────────────
    @Test
    void postUpdate_callsIndexDriverWithAutoUpdateSource() {
        listener.onPostUpdate(driverWithId(7L));
        fireAfterCommit();

        verify(mockIndexService).indexDriver(7L, "auto_crud_update");
    }

    @Test
    void postUpdate_doesNotCallRemoveFromIndex() {
        listener.onPostUpdate(driverWithId(1L));
        fireAfterCommit();

        verify(mockIndexService, never()).removeFromIndex(anyLong());
    }

    // ── @PostRemove ───────────────────────────────────────────────────────────
    @Test
    void postRemove_callsRemoveFromIndex() {
        listener.onPostRemove(driverWithId(42L));
        fireAfterCommit();

        verify(mockIndexService).removeFromIndex(42L);
    }

    @Test
    void postRemove_doesNotCallIndexDriver() {
        listener.onPostRemove(driverWithId(1L));
        fireAfterCommit();

        verify(mockIndexService, never()).indexDriver(anyLong(), anyString());
    }

    // ── Soft dependency: ES failure must not crash PG transaction ─────────────
    @Test
    void postPersist_esFailure_doesNotThrow() {
        doThrow(new RuntimeException("ES unavailable"))
                .when(mockIndexService).indexDriver(anyLong(), anyString());

        listener.onPostPersist(driverWithId(1L));
        assertDoesNotThrow(this::fireAfterCommit);
    }

    @Test
    void postUpdate_esFailure_doesNotThrow() {
        doThrow(new RuntimeException("ES unavailable"))
                .when(mockIndexService).indexDriver(anyLong(), anyString());

        listener.onPostUpdate(driverWithId(1L));
        assertDoesNotThrow(this::fireAfterCommit);
    }

    @Test
    void postRemove_esFailure_doesNotThrow() {
        doThrow(new RuntimeException("ES unavailable"))
                .when(mockIndexService).removeFromIndex(anyLong());

        listener.onPostRemove(driverWithId(1L));
        assertDoesNotThrow(this::fireAfterCommit);
    }

    // ── Null guard: service not yet injected (startup race) ───────────────────
    @Test
    void postPersist_nullService_doesNotThrow() {
        listener.setDriverIndexService(null);
        assertDoesNotThrow(() -> listener.onPostPersist(driverWithId(1L)));
    }

    @Test
    void postUpdate_nullService_doesNotThrow() {
        listener.setDriverIndexService(null);
        assertDoesNotThrow(() -> listener.onPostUpdate(driverWithId(1L)));
    }

    @Test
    void postRemove_nullService_doesNotThrow() {
        listener.setDriverIndexService(null);
        assertDoesNotThrow(() -> listener.onPostRemove(driverWithId(1L)));
    }

    // ── No double DRIVER_DELETED: postRemove must NOT call indexDriver ─────────
    @Test
    void postRemove_neverCallsIndexDriver_noDoubleEvent() {
        listener.onPostRemove(driverWithId(99L));
        fireAfterCommit();

        verify(mockIndexService, never()).indexDriver(anyLong(), anyString());
        verify(mockIndexService, times(1)).removeFromIndex(99L);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private Driver driverWithId(Long id) {
        Driver d = new Driver();
        d.setId(id);
        return d;
    }
}
