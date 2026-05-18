package com.team10.uber.driver.service;

import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.elastic.DriverSearchDocument;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverRepository;
import com.team10.uber.driver.repository.elastic.DriverSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for S2-F11: Index Driver for Search */
class DriverIndexServiceTest {

    private DriverRepository driverRepository;
    private DriverSearchRepository searchRepository;
    private MongoEventLogger mongoEventLogger;
    private DriverIndexService driverIndexService;

    @BeforeEach
    void setUp() {
        driverRepository   = mock(DriverRepository.class);
        searchRepository   = mock(DriverSearchRepository.class);
        mongoEventLogger   = mock(MongoEventLogger.class);
        driverIndexService = new DriverIndexService(driverRepository, searchRepository, mongoEventLogger);
        driverIndexService.initObservers();
    }

    private Driver driver(Long id, String description, String vehicleType) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setRating(4.5);
        d.setVehicleDetails(Map.of("description", description, "vehicleType", vehicleType));
        return d;
    }

    // ── Spec scenario (a): index with description ─────────────────────────────

    @Test
    @DisplayName("Spec scenario a — index driver, saves ES document with correct fields")
    void indexDriver_savesSearchDocument() {
        Driver d = driver(1L, "luxury toyota camry", "SEDAN");
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        driverIndexService.indexDriver(1L, "explicit");

        verify(searchRepository).save(argThat(doc ->
                doc.getId().equals("1")
                        && doc.getName().equals("Driver 1")
                        && doc.getVehicleType().equals("SEDAN")
                        && doc.getDescription().equals("luxury toyota camry")));
    }

    // ── Spec scenario (b): missing description key defaults to empty string ──

    @Test
    @DisplayName("Spec scenario b — missing description defaults to empty string")
    void indexDriver_missingDescription_usesEmptyString() {
        Driver d = new Driver();
        d.setId(2L);
        d.setName("Driver 2");
        d.setVehicleDetails(Map.of("vehicleType", "SUV")); // no description key
        when(driverRepository.findById(2L)).thenReturn(Optional.of(d));

        driverIndexService.indexDriver(2L, "explicit");

        verify(searchRepository).save(argThat(doc -> doc.getDescription().isEmpty()));
    }

    // ── Spec scenario (c): driver not found → 404 ─────────────────────────────

    @Test
    @DisplayName("Spec scenario c — driver not found throws 404")
    void indexDriver_driverNotFound_throws404() {
        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverIndexService.indexDriver(999L, "explicit"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(searchRepository, never()).save(any());
    }

    // ── Observer notified with INDEXED event ──────────────────────────────────

    @Test
    @DisplayName("INDEXED event fired after successful indexing")
    void indexDriver_notifiesObserver() {
        Driver d = driver(1L, "desc", "SEDAN");
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        driverIndexService.indexDriver(1L, "explicit");

        verify(mongoEventLogger).onEvent(eq("INDEXED"), any());
    }

    // ── Source field propagated ───────────────────────────────────────────────

    @Test
    @DisplayName("Source 'explicit' propagated in event payload")
    void indexDriver_sourceFieldInPayload() {
        Driver d = driver(1L, "desc", "SEDAN");
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        driverIndexService.indexDriver(1L, "explicit");

        // Observer called with a map containing source=explicit
        verify(mongoEventLogger).onEvent(eq("INDEXED"), argThat(payload -> {
            if (payload instanceof Map<?, ?> map) {
                return "explicit".equals(map.get("source"));
            }
            return false;
        }));
    }

    // ── Auto-source: auto_crud_create ─────────────────────────────────────────

    @Test
    @DisplayName("Source 'auto_crud_create' stored for CRUD-triggered indexing")
    void indexDriver_autoCrudSource() {
        Driver d = driver(1L, "desc", "SEDAN");
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        driverIndexService.indexDriver(1L, "auto_crud_create");

        verify(mongoEventLogger).onEvent(eq("INDEXED"), argThat(payload -> {
            if (payload instanceof Map<?, ?> map) {
                return "auto_crud_create".equals(map.get("source"));
            }
            return false;
        }));
    }

    // ── Null vehicleDetails → no NPE ──────────────────────────────────────────

    @Test
    @DisplayName("Null vehicleDetails — indexing succeeds without NPE")
    void indexDriver_nullVehicleDetails_noNpe() {
        Driver d = new Driver();
        d.setId(3L);
        d.setName("Driver 3");
        d.setVehicleDetails(null);
        when(driverRepository.findById(3L)).thenReturn(Optional.of(d));

        assertThatCode(() -> driverIndexService.indexDriver(3L, "explicit"))
                .doesNotThrowAnyException();

        verify(searchRepository).save(any(DriverSearchDocument.class));
    }
}
