package com.team10.uber.driver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.dto.TopRatedDriverDTO;
import com.team10.uber.driver.messaging.DriverEventPublisher;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for S2-F6: Top Rated Drivers Report (M3 Feign enrichment) */
class DriverServiceS2F6Test {

    private DriverRepository driverRepository;
    private RideServiceClient rideServiceClient;
    private RedisCacheService cache;
    private DriverService driverService;

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

    private Object[] candidateRow(long driverId, String name, double rating) {
        return new Object[]{driverId, name, rating};
    }

    // ── Spec scenario: 3 drivers, limit=2, 4.9 first ─────────────────────────

    @Test
    @DisplayName("Spec scenario — limit=2, returns top 2 ordered by rating desc")
    void topRated_limit2_returns4_9First() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findTopRatedDriverCandidates(100)).thenReturn(List.of(
                candidateRow(1L, "Alpha", 4.9),
                candidateRow(2L, "Beta",  4.5),
                candidateRow(3L, "Gamma", 4.2)
        ));
        when(rideServiceClient.getDriverCompletedRideCount(1L)).thenReturn(50L);
        when(rideServiceClient.getDriverCompletedRideCount(2L)).thenReturn(40L);
        when(rideServiceClient.getDriverCompletedRideCount(3L)).thenReturn(30L);

        List<TopRatedDriverDTO> result = driverService.getTopRatedDrivers(2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRating()).isEqualTo(4.9);
        assertThat(result.get(0).getName()).isEqualTo("Alpha");
    }

    // ── Spec scenario: limit=10 → all 3 returned ─────────────────────────────

    @Test
    @DisplayName("Spec scenario — limit=10 returns all 3 drivers")
    void topRated_limitLargerThanList_returnsAll() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findTopRatedDriverCandidates(100)).thenReturn(List.of(
                candidateRow(1L, "Alpha", 4.9),
                candidateRow(2L, "Beta",  4.5),
                candidateRow(3L, "Gamma", 4.2)
        ));
        when(rideServiceClient.getDriverCompletedRideCount(anyLong())).thenReturn(10L);

        List<TopRatedDriverDTO> result = driverService.getTopRatedDrivers(10);

        assertThat(result).hasSize(3);
    }

    // ── Feign failure for one driver → uses 0 (soft fail) ────────────────────

    @Test
    @DisplayName("Feign failure for one driver → that driver gets totalRides=0")
    void topRated_feignFailureOneDriver_softFail() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findTopRatedDriverCandidates(100)).thenReturn(List.of(
                candidateRow(1L, "Alpha", 4.9),
                candidateRow(2L, "Beta",  4.5)
        ));
        when(rideServiceClient.getDriverCompletedRideCount(1L)).thenReturn(50L);
        when(rideServiceClient.getDriverCompletedRideCount(2L))
                .thenThrow(mock(FeignException.class));

        List<TopRatedDriverDTO> result = driverService.getTopRatedDrivers(10);

        assertThat(result).hasSize(2);
        TopRatedDriverDTO beta = result.stream()
                .filter(d -> d.getName().equals("Beta"))
                .findFirst().orElseThrow();
        assertThat(beta.getTotalRides()).isZero();
    }

    // ── Ordering: rating desc, totalRides as tiebreaker ──────────────────────

    @Test
    @DisplayName("Same rating — higher totalRides comes first")
    void topRated_samRating_totalRidesTiebreaker() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findTopRatedDriverCandidates(100)).thenReturn(List.of(
                candidateRow(1L, "LessRides", 4.9),
                candidateRow(2L, "MoreRides", 4.9)
        ));
        when(rideServiceClient.getDriverCompletedRideCount(1L)).thenReturn(10L);
        when(rideServiceClient.getDriverCompletedRideCount(2L)).thenReturn(50L);

        List<TopRatedDriverDTO> result = driverService.getTopRatedDrivers(2);

        assertThat(result.get(0).getName()).isEqualTo("MoreRides");
    }

    // ── Cache tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cache hit — Feign never called")
    void topRated_cacheHit_skipsFeignAndRepo() {
        List<TopRatedDriverDTO> cached = List.of(
                TopRatedDriverDTO.builder().driverId(1L).name("A").rating(4.9).totalRides(50L).build());
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(cached);

        List<TopRatedDriverDTO> result = driverService.getTopRatedDrivers(5);

        assertThat(result).isSameAs(cached);
        verify(rideServiceClient, never()).getDriverCompletedRideCount(any());
    }

    @Test
    @DisplayName("Result stored in cache with 10-minute TTL")
    void topRated_resultStoredInCache() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findTopRatedDriverCandidates(100)).thenReturn(List.of());

        driverService.getTopRatedDrivers(5);

        verify(cache).put(anyString(), anyList(), eq(10 * 60L));
    }

    // ── Empty DB → empty result ───────────────────────────────────────────────

    @Test
    @DisplayName("No drivers in DB → empty list")
    void topRated_noDrivers_returnsEmpty() {
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(null);
        when(driverRepository.findTopRatedDriverCandidates(100)).thenReturn(List.of());

        List<TopRatedDriverDTO> result = driverService.getTopRatedDrivers(5);

        assertThat(result).isEmpty();
    }
}
