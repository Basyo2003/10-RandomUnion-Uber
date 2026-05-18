package com.team10.uber.driver.service;

import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.messaging.DriverEventPublisher;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for Driver CRUD operations */
class DriverCrudTest {

    private DriverRepository driverRepository;
    private RedisCacheService cache;
    private DriverService driverService;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        cache = mock(RedisCacheService.class);
        driverService = new DriverService(
                driverRepository,
                mock(DriverDocumentRepository.class),
                mock(MongoEventLogger.class),
                cache,
                mock(ElasticsearchOperations.class),
                mock(RideServiceClient.class),
                mock(DriverEventPublisher.class)
        );
        driverService.initObservers();
    }

    private Driver driver(Long id, String email, String license) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setEmail(email);
        d.setPhone("0100000000" + id);
        d.setLicenseNumber(license);
        d.setStatus(DriverStatus.AVAILABLE);
        d.setRating(0.0);
        d.setTotalRatings(0);
        return d;
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Create driver — saved and returned")
    void create_savesAndReturnsDriver() {
        Driver d = driver(1L, "d1@test.com", "LIC-001");
        when(driverRepository.save(any())).thenReturn(d);

        Driver result = driverService.createDriver(d);

        assertThat(result.getId()).isEqualTo(1L);
        verify(driverRepository).save(d);
    }

    @Test
    @DisplayName("Create driver — duplicate email throws 400")
    void create_duplicateEmail_throws400() {
        Driver d = driver(null, "dup@test.com", "LIC-002");
        when(driverRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("constraint: email"));

        assertThatThrownBy(() -> driverService.createDriver(d))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("Create driver — duplicate license number throws 400")
    void create_duplicateLicense_throws400() {
        Driver d = driver(null, "d2@test.com", "LIC-DUP");
        when(driverRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("constraint: license_number"));

        assertThatThrownBy(() -> driverService.createDriver(d))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Create driver — duplicate phone throws 400")
    void create_duplicatePhone_throws400() {
        Driver d = driver(null, "d3@test.com", "LIC-003");
        when(driverRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("constraint: phone"));

        assertThatThrownBy(() -> driverService.createDriver(d))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── GET BY ID ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Get by ID — returns driver")
    void getById_returnsDriver() {
        Driver d = driver(1L, "d1@test.com", "LIC-001");
        when(cache.get("driver::1", Driver.class)).thenReturn(null);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        Driver result = driverService.getDriverById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Get by ID — cache hit on second call")
    void getById_cacheHitOnSecondCall() {
        Driver d = driver(1L, "d1@test.com", "LIC-001");
        when(cache.get("driver::1", Driver.class)).thenReturn(d);

        Driver result = driverService.getDriverById(1L);

        assertThat(result).isSameAs(d);
        verify(driverRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Get by ID — not found throws 404")
    void getById_notFound_throws404() {
        when(cache.get("driver::1", Driver.class)).thenReturn(null);
        when(driverRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getDriverById(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Get by ID — result stored in cache with 15-minute TTL")
    void getById_storesResultInCache() {
        Driver d = driver(1L, "d1@test.com", "LIC-001");
        when(cache.get("driver::1", Driver.class)).thenReturn(null);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        driverService.getDriverById(1L);

        verify(cache).put("driver::1", d, 15 * 60L);
    }

    // ── GET ALL ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Get all drivers returns repository result")
    void getAll_returnsAllDrivers() {
        List<Driver> all = List.of(driver(1L, "d1@test.com", "L1"), driver(2L, "d2@test.com", "L2"));
        when(driverRepository.findAll()).thenReturn(all);

        List<Driver> result = driverService.getAllDrivers();

        assertThat(result).hasSize(2);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Update driver — all fields updated and saved")
    void update_updatesAllFields() {
        Driver existing = driver(1L, "old@test.com", "LIC-OLD");
        Driver updated  = driver(1L, "new@test.com", "LIC-NEW");
        updated.setName("New Name");
        when(cache.get("driver::1", Driver.class)).thenReturn(null);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(driverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Driver result = driverService.updateDriver(1L, updated);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getEmail()).isEqualTo("new@test.com");
        verify(driverRepository).save(any());
    }

    @Test
    @DisplayName("Update driver — cache evicted after update")
    void update_cacheEvictedAfterUpdate() {
        Driver existing = driver(1L, "old@test.com", "LIC-OLD");
        when(cache.get("driver::1", Driver.class)).thenReturn(null);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(driverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        driverService.updateDriver(1L, driver(1L, "new@test.com", "LIC-NEW"));

        verify(cache).evict("driver::1");
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Delete driver — delete called on repository")
    void delete_callsRepository() {
        Driver d = driver(1L, "d1@test.com", "LIC-001");
        when(cache.get("driver::1", Driver.class)).thenReturn(null);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));

        driverService.deleteDriver(1L);

        verify(driverRepository).delete(d);
    }

    @Test
    @DisplayName("Delete driver — not found throws 404")
    void delete_notFound_throws404() {
        when(cache.get("driver::1", Driver.class)).thenReturn(null);
        when(driverRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.deleteDriver(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
