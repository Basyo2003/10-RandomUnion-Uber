package com.team10.uber.driver.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.*;

/**
 * M2 reflection test — verifies that the three dashboard DTOs expose builder()/build() as required
 * by the Builder design pattern grading criteria.
 */
class DriverDtoBuildersTest {

    // ── DriverEarningsDTO ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DriverEarningsDTO has static builder() method")
    void driverEarningsDto_hasStaticBuilderMethod() throws Exception {
        Method builder = DriverEarningsDTO.class.getMethod("builder");
        assertThat(Modifier.isStatic(builder.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("DriverEarningsDTO.builder().build() returns DriverEarningsDTO instance")
    void driverEarningsDto_builderBuildReturnsInstance() {
        Object result = DriverEarningsDTO.builder().build();
        assertThat(result).isInstanceOf(DriverEarningsDTO.class);
    }

    @Test
    @DisplayName("DriverEarningsDTO builder sets and reads fields correctly")
    void driverEarningsDto_builderSetsFields() {
        DriverEarningsDTO dto = DriverEarningsDTO.builder()
                .driverId(1L)
                .name("Test Driver")
                .totalRides(5L)
                .totalEarnings(350.0)
                .averageFare(70.0)
                .build();

        assertThat(dto.getDriverId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Test Driver");
        assertThat(dto.getTotalRides()).isEqualTo(5L);
        assertThat(dto.getTotalEarnings()).isEqualTo(350.0);
        assertThat(dto.getAverageFare()).isEqualTo(70.0);
    }

    // ── TopRatedDriverDTO ─────────────────────────────────────────────────────

    @Test
    @DisplayName("TopRatedDriverDTO has static builder() method")
    void topRatedDriverDto_hasStaticBuilderMethod() throws Exception {
        Method builder = TopRatedDriverDTO.class.getMethod("builder");
        assertThat(Modifier.isStatic(builder.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("TopRatedDriverDTO.builder().build() returns TopRatedDriverDTO instance")
    void topRatedDriverDto_builderBuildReturnsInstance() {
        Object result = TopRatedDriverDTO.builder().build();
        assertThat(result).isInstanceOf(TopRatedDriverDTO.class);
    }

    @Test
    @DisplayName("TopRatedDriverDTO builder sets and reads fields correctly")
    void topRatedDriverDto_builderSetsFields() {
        TopRatedDriverDTO dto = TopRatedDriverDTO.builder()
                .driverId(2L)
                .name("Top Driver")
                .rating(4.9)
                .totalRides(50L)
                .build();

        assertThat(dto.getDriverId()).isEqualTo(2L);
        assertThat(dto.getName()).isEqualTo("Top Driver");
        assertThat(dto.getRating()).isEqualTo(4.9);
        assertThat(dto.getTotalRides()).isEqualTo(50L);
    }

    // ── DriverDashboardDTO ────────────────────────────────────────────────────

    @Test
    @DisplayName("DriverDashboardDTO has static builder() method")
    void driverDashboardDto_hasStaticBuilderMethod() throws Exception {
        Method builder = DriverDashboardDTO.class.getMethod("builder");
        assertThat(Modifier.isStatic(builder.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("DriverDashboardDTO.builder().build() returns DriverDashboardDTO instance")
    void driverDashboardDto_builderBuildReturnsInstance() {
        Object result = DriverDashboardDTO.builder().build();
        assertThat(result).isInstanceOf(DriverDashboardDTO.class);
    }

    @Test
    @DisplayName("DriverDashboardDTO builder sets and reads all fields correctly")
    void driverDashboardDto_builderSetsFields() {
        DriverDashboardDTO dto = DriverDashboardDTO.builder()
                .driverId(3L)
                .name("Dashboard Driver")
                .totalRides(10L)
                .totalEarnings(1000.0)
                .averageRideFare(100.0)
                .averageRating(4.5)
                .totalRatings(100)
                .build();

        assertThat(dto.getDriverId()).isEqualTo(3L);
        assertThat(dto.getName()).isEqualTo("Dashboard Driver");
        assertThat(dto.getTotalRides()).isEqualTo(10L);
        assertThat(dto.getTotalEarnings()).isEqualTo(1000.0);
        assertThat(dto.getAverageRideFare()).isEqualTo(100.0);
        assertThat(dto.getAverageRating()).isEqualTo(4.5);
        assertThat(dto.getTotalRatings()).isEqualTo(100);
    }
}
