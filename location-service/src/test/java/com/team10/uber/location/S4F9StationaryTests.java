package com.team10.uber.location;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F9StationaryTests {

    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final String LOCATION_SERVICE_URL = "http://localhost:8084";

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/uberdb";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static String token;

    @BeforeAll
    static void setupToken() throws Exception {
        registerUserIfNeeded();
        token = loginAndGetToken();

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    // =====================================================
    // TC292
    // Stationary returns drivers with last metadata.speed below maxSpeed
    // =====================================================
    @Test
    @Order(1)
    void TC292_stationaryReturnsDriversWithLastMetadataSpeedBelowMaxSpeed() throws Exception {
        long d1 = seedDriver("DL292A");
        long d2 = seedDriver("DL292B");

        seedLocationMinutesAgo(d1, 30.01, 31.01, 2.0, 0);
        seedLocationMinutesAgo(d2, 30.02, 31.02, 50.0, 0);

        HttpResponse<String> httpResponse = getWithAuth(
                LOCATION_SERVICE_URL + "/api/locations/stationary?maxSpeed=5&sinceMinutes=10"
        );

        System.out.println("TC292 response body = " + httpResponse.body());

        assertEquals(200, httpResponse.statusCode(), httpResponse.body());

        List<Map<String, Object>> response = objectMapper.readValue(
                httpResponse.body(),
                new TypeReference<>() {}
        );

        assertTrue(
                containsDriver(response, d1),
                "TC292 failed: slow recent driver d1 should be included"
        );

        assertFalse(
                containsDriver(response, d2),
                "TC292 failed: fast recent driver d2 should be excluded"
        );
    }

    // =====================================================
    // TC293
    // Stationary with sinceMinutes=-1 returns 400
    // =====================================================
    @Test
    @Order(2)
    void TC293_stationaryWithNegativeSinceMinutesReturns400() throws Exception {
        HttpResponse<String> httpResponse = getWithAuth(
                LOCATION_SERVICE_URL + "/api/locations/stationary?maxSpeed=5&sinceMinutes=-1"
        );

        assertEquals(400, httpResponse.statusCode(), httpResponse.body());
    }

    // =====================================================
    // TC358
    // Stationary excludes drivers whose last update is older than sinceMinutes
    // =====================================================
    @Test
    @Order(3)
    void TC358_stationaryExcludesDriversWhoseLastUpdateIsOlderThanSinceMinutes() throws Exception {
        long d1 = seedDriver("DL358");

        seedLocationHoursAgo(d1, 30.03, 31.03, 2.0, 2);

        HttpResponse<String> httpResponse = getWithAuth(
                LOCATION_SERVICE_URL + "/api/locations/stationary?maxSpeed=5&sinceMinutes=5"
        );

        System.out.println("TC358 response body = " + httpResponse.body());

        assertEquals(200, httpResponse.statusCode(), httpResponse.body());

        List<Map<String, Object>> response = objectMapper.readValue(
                httpResponse.body(),
                new TypeReference<>() {}
        );

        assertFalse(
                containsDriver(response, d1),
                "TC358 failed: stale slow driver should be excluded because timestamp is older than sinceMinutes"
        );
    }

    // =====================================================
    // TC402
    // DP-4 Builder: Location-service M1 DTOs have Builder
    // =====================================================
    @Test
    @Order(4)
    void TC402_locationServiceM1DtosHaveBuilder() throws Exception {
        assertDtoHasBuilder("com.team10.uber.location.dto.NearbyDriverDTO");
        assertDtoHasBuilder("com.team10.uber.location.dto.DriverMovementSummaryDTO");
        assertDtoHasBuilder("com.team10.uber.location.dto.StationaryDriverDTO");
    }

    private static void assertDtoHasBuilder(String className) throws Exception {
        Class<?> dtoClass = Class.forName(className);

        Method builderMethod = dtoClass.getDeclaredMethod("builder");

        assertTrue(
                java.lang.reflect.Modifier.isStatic(builderMethod.getModifiers()),
                className + " builder() method must be static"
        );

        Object builder = builderMethod.invoke(null);

        assertNotNull(
                builder,
                className + " builder() returned null"
        );

        Method buildMethod = builder.getClass().getDeclaredMethod("build");
        Object dto = buildMethod.invoke(builder);

        assertTrue(
                dtoClass.isInstance(dto),
                className + " build() must return the DTO type"
        );
    }

    // =====================================================
    // HTTP helpers
    // =====================================================

    private static void registerUserIfNeeded() throws Exception {
        String body = """
                {
                  "name": "Ahmed Rider",
                  "email": "ahmed.rider@test.com",
                  "password": "123456",
                  "phone": "01000000001",
                  "preferences": {
                    "language": "en",
                    "paymentMethod": "cash",
                    "notifications": true
                  }
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USER_SERVICE_URL + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();

        assertTrue(
                statusCode == 200 || statusCode == 201 || statusCode == 400 || statusCode == 409,
                "Register should succeed or user may already exist. Got: " + statusCode + "\nBody: " + response.body()
        );
    }

    private static String loginAndGetToken() throws Exception {
        String body = """
                {
                  "email": "ahmed.rider@test.com",
                  "password": "123456"
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USER_SERVICE_URL + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, httpResponse.statusCode(), httpResponse.body());

        Map<String, Object> response = objectMapper.readValue(
                httpResponse.body(),
                new TypeReference<>() {}
        );

        Object accessToken = response.get("accessToken");
        Object token = response.get("token");
        Object jwt = response.get("jwt");
        Object bearerToken = response.get("bearerToken");

        if (accessToken != null) {
            return removeBearerPrefix(accessToken.toString());
        }

        if (token != null) {
            return removeBearerPrefix(token.toString());
        }

        if (jwt != null) {
            return removeBearerPrefix(jwt.toString());
        }

        if (bearerToken != null) {
            return removeBearerPrefix(bearerToken.toString());
        }

        throw new IllegalStateException("Could not find token field in login response: " + response);
    }

    private static HttpResponse<String> getWithAuth(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String removeBearerPrefix(String value) {
        if (value.startsWith("Bearer ")) {
            return value.substring("Bearer ".length());
        }

        return value;
    }

    // =====================================================
    // Database helpers
    // =====================================================

    private static long seedDriver(String label) throws Exception {
        String unique = label + "_" + System.currentTimeMillis() + "_" + Math.abs(label.hashCode());

        String sql = """
                INSERT INTO drivers
                (created_at, name, email, phone, license_number, status, rating, total_ratings, vehicle_details)
                VALUES (CURRENT_TIMESTAMP AT TIME ZONE 'UTC', ?, ?, ?, ?, 'AVAILABLE', 5.0, 1, '{}'::jsonb)
                RETURNING id
                """;

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, unique);
            ps.setString(2, unique.toLowerCase() + "@test.com");
            ps.setString(3, "010" + Math.abs(unique.hashCode()));
            ps.setString(4, "LIC-" + unique);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong("id");
            }
        }
    }

    private static void seedLocationMinutesAgo(
            long driverId,
            double latitude,
            double longitude,
            double speed,
            int minutesAgo
    ) throws Exception {
        String sql = """
                INSERT INTO locations
                (driver_id, latitude, longitude, timestamp, metadata)
                VALUES (
                    ?,
                    ?,
                    ?,
                    (CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - (? * INTERVAL '1 minute'),
                    CAST(? AS jsonb)
                )
                """;

        String metadata = """
                {
                  "speed": %s,
                  "heading": 100.0,
                  "accuracy": 5.0
                }
                """.formatted(speed);

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, driverId);
            ps.setDouble(2, latitude);
            ps.setDouble(3, longitude);
            ps.setInt(4, minutesAgo);
            ps.setString(5, metadata);

            ps.executeUpdate();
        }
    }

    private static void seedLocationHoursAgo(
            long driverId,
            double latitude,
            double longitude,
            double speed,
            int hoursAgo
    ) throws Exception {
        String sql = """
                INSERT INTO locations
                (driver_id, latitude, longitude, timestamp, metadata)
                VALUES (
                    ?,
                    ?,
                    ?,
                    (CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - (? * INTERVAL '1 hour'),
                    CAST(? AS jsonb)
                )
                """;

        String metadata = """
                {
                  "speed": %s,
                  "heading": 100.0,
                  "accuracy": 5.0
                }
                """.formatted(speed);

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, driverId);
            ps.setDouble(2, latitude);
            ps.setDouble(3, longitude);
            ps.setInt(4, hoursAgo);
            ps.setString(5, metadata);

            ps.executeUpdate();
        }
    }

    private static Connection getConnection() throws Exception {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static boolean containsDriver(List<Map<String, Object>> response, long driverId) {
        return response.stream().anyMatch(row -> {
            Object value = row.get("driverId");

            if (value == null) {
                value = row.get("driver_id");
            }

            if (value == null) {
                value = row.get("id");
            }

            if (value == null) {
                return false;
            }

            return Long.parseLong(value.toString()) == driverId;
        });
    }
}