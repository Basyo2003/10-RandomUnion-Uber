package com.team10.uber.driver.service;

import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.messaging.DriverEventPublisher;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for DriverDocument CRUD operations */
class DriverDocumentCrudTest {

    private DriverDocumentRepository driverDocumentRepository;
    private DriverRepository driverRepository;
    private DriverDocumentService driverDocumentService;

    @BeforeEach
    void setUp() {
        driverDocumentRepository = mock(DriverDocumentRepository.class);
        driverRepository         = mock(DriverRepository.class);
        driverDocumentService = new DriverDocumentService(
                driverDocumentRepository,
                driverRepository,
                mock(MongoEventLogger.class),
                mock(RedisCacheService.class),
                mock(DriverEventPublisher.class),
                mock(UserServiceClient.class)
        );
        driverDocumentService.initObservers();
    }

    private Driver driver(Long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        return d;
    }

    private DriverDocument document(Long id, Long driverId) {
        Driver d = driver(driverId);
        DriverDocument doc = new DriverDocument();
        doc.setId(id);
        doc.setDriver(d);
        doc.setExpiryDate(LocalDate.now().plusDays(30));
        doc.setDocumentUrl("http://docs/" + id);
        return doc;
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Create document — links to driver and saves")
    void createDoc_linksDriverAndSaves() {
        Driver d = driver(1L);
        DriverDocument doc = new DriverDocument();
        doc.setExpiryDate(LocalDate.now().plusDays(30));
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));
        when(driverDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriverDocument result = driverDocumentService.createDriverDocument(1L, doc);

        assertThat(result.getDriver().getId()).isEqualTo(1L);
        verify(driverDocumentRepository).save(doc);
    }

    @Test
    @DisplayName("Create document — driver not found throws 404")
    void createDoc_driverNotFound_throws404() {
        when(driverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverDocumentService.createDriverDocument(999L, new DriverDocument()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(driverDocumentRepository, never()).save(any());
    }

    // ── GET BY DRIVER ID ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Get documents by driverId — returns list")
    void getByDriverId_returnsList() {
        DriverDocument doc1 = document(1L, 1L);
        DriverDocument doc2 = document(2L, 1L);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));
        when(driverDocumentRepository.findByDriverId(1L)).thenReturn(List.of(doc1, doc2));

        List<DriverDocument> result = driverDocumentService.getDriverDocumentsByDriverId(1L);

        assertThat(result).hasSize(2);
    }

    // ── GET BY ID ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Get document by ID — returns document")
    void getById_returnsDocument() {
        DriverDocument doc = document(10L, 1L);
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(doc));

        DriverDocument result = driverDocumentService.getDriverDocumentById(10L);

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Get document by ID — not found throws 404")
    void getById_notFound_throws404() {
        when(driverDocumentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverDocumentService.getDriverDocumentById(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Delete document — delete called on repository")
    void deleteDoc_callsRepository() {
        DriverDocument doc = document(10L, 1L);
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(doc));

        driverDocumentService.deleteDriverDocument(10L);

        verify(driverDocumentRepository).delete(doc);
    }

    @Test
    @DisplayName("Delete document — not found throws 404")
    void deleteDoc_notFound_throws404() {
        when(driverDocumentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverDocumentService.deleteDriverDocument(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Update document — saves updated document")
    void updateDoc_savesUpdated() {
        Driver d = driver(1L);
        DriverDocument existing = document(10L, 1L);
        DriverDocument updated  = document(10L, 1L);
        updated.setDocumentUrl("http://new-url/10");
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));
        when(driverDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriverDocument result = driverDocumentService.updateDriverDocument(10L, updated);

        assertThat(result.getDocumentUrl()).isEqualTo("http://new-url/10");
        verify(driverDocumentRepository).save(any());
    }
}
