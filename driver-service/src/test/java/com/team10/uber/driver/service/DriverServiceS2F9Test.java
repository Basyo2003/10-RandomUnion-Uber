package com.team10.uber.driver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.dto.DriverDocumentAlertDTO;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.messaging.DriverEventPublisher;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Unit tests for S2-F9: Get Drivers with Expired Documents */
class DriverServiceS2F9Test {

    private DriverRepository driverRepository;
    private DriverDocumentRepository driverDocumentRepository;
    private RedisCacheService cache;
    private DriverService driverService;

    @BeforeEach
    void setUp() {
        driverRepository         = mock(DriverRepository.class);
        driverDocumentRepository = mock(DriverDocumentRepository.class);
        cache                    = mock(RedisCacheService.class);
        driverService = new DriverService(
                driverRepository,
                driverDocumentRepository,
                mock(MongoEventLogger.class),
                cache,
                mock(ElasticsearchOperations.class),
                mock(RideServiceClient.class),
                mock(DriverEventPublisher.class)
        );
        driverService.initObservers();
    }

    private Driver driver(Long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setStatus(DriverStatus.AVAILABLE);
        return d;
    }

    private DriverDocument expiredDoc(Long id, Driver owner) {
        DriverDocument doc = new DriverDocument();
        doc.setId(id);
        doc.setDriver(owner);
        doc.setExpiryDate(LocalDate.now().minusDays(5));
        return doc;
    }

    // ── Spec scenario: Driver A (1 expired), Driver B (0 expired), Driver C (2 expired) ─

    @Test
    @DisplayName("Spec — Driver A (1 expired) + Driver C (2 expired); Driver B excluded")
    void expiredDocs_specScenario() {
        Driver a = driver(1L);
        Driver c = driver(3L);

        DriverDocument docA1 = expiredDoc(10L, a);
        DriverDocument docC1 = expiredDoc(30L, c);
        DriverDocument docC2 = expiredDoc(31L, c);

        when(cache.get(eq("S2-F9::all"), any(TypeReference.class))).thenReturn(null);
        when(driverDocumentRepository.findExpiredDocuments()).thenReturn(List.of(docA1, docC1, docC2));
        when(driverRepository.findById(1L)).thenReturn(Optional.of(a));
        when(driverRepository.findById(3L)).thenReturn(Optional.of(c));
        when(cache.get("driver::1", Driver.class)).thenReturn(null);
        when(cache.get("driver::3", Driver.class)).thenReturn(null);

        List<DriverDocumentAlertDTO> result = driverService.getDriversWithExpiredDocuments();

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(dto -> dto.getDriverId().equals(1L) && dto.getExpiredCount() == 1);
        assertThat(result).anyMatch(dto -> dto.getDriverId().equals(3L) && dto.getExpiredCount() == 2);
    }

    // ── Empty DB → empty list ─────────────────────────────────────────────────

    @Test
    @DisplayName("Spec — no expired documents → empty list")
    void expiredDocs_none_returnsEmptyList() {
        when(cache.get(eq("S2-F9::all"), any(TypeReference.class))).thenReturn(null);
        when(driverDocumentRepository.findExpiredDocuments()).thenReturn(List.of());

        List<DriverDocumentAlertDTO> result = driverService.getDriversWithExpiredDocuments();

        assertThat(result).isEmpty();
    }

    // ── Cache hit ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cache hit — repository never called")
    void expiredDocs_cacheHit_skipsRepository() {
        List<DriverDocumentAlertDTO> cached = List.of();
        when(cache.get(eq("S2-F9::all"), any(TypeReference.class))).thenReturn(cached);

        List<DriverDocumentAlertDTO> result = driverService.getDriversWithExpiredDocuments();

        assertThat(result).isSameAs(cached);
        verify(driverDocumentRepository, never()).findExpiredDocuments();
    }

    // ── Cache store ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Result stored in cache with 10-minute TTL")
    void expiredDocs_resultStoredInCache() {
        when(cache.get(eq("S2-F9::all"), any(TypeReference.class))).thenReturn(null);
        when(driverDocumentRepository.findExpiredDocuments()).thenReturn(List.of());

        driverService.getDriversWithExpiredDocuments();

        verify(cache).put(eq("S2-F9::all"), any(), eq(10 * 60L));
    }

    // ── Only expired docs grouped per driver ──────────────────────────────────

    @Test
    @DisplayName("Only drivers with expired documents are returned (not all drivers)")
    void expiredDocs_onlyDriversWithExpiredIncluded() {
        Driver a = driver(1L);
        DriverDocument docA = expiredDoc(10L, a);

        when(cache.get(eq("S2-F9::all"), any(TypeReference.class))).thenReturn(null);
        when(driverDocumentRepository.findExpiredDocuments()).thenReturn(List.of(docA));
        when(driverRepository.findById(1L)).thenReturn(Optional.of(a));
        when(cache.get("driver::1", Driver.class)).thenReturn(null);

        List<DriverDocumentAlertDTO> result = driverService.getDriversWithExpiredDocuments();

        // Driver B (no expired docs) must not appear
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriverId()).isEqualTo(1L);
    }
}
