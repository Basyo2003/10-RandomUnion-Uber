package com.team10.uber.location;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F3NearbyDriversTests {

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

    @BeforeEach
    void resetBeforeEach() throws Exception {
        flushRedisCache();
        cleanTestLocationsAndDrivers();
    }

    // =====================================================
    // TC279
    // Nearby returns drivers within radiusKm
    // =====================================================
    @Test
    @Order(279)
    void TC279_nearbyReturnsDriversWithinRadiusKm() throws Exception {
        long nearDriver = seedDriver("TC279_NEAR");
        long farDriver = seedDriver("TC279_FAR");

        seedLocation(nearDriver, 30.045, 31.236, 20.0);
        seedLocation(farDriver, 31.500, 32.500, 20.0);

        HttpResponse<String> response = getNearby(30.044, 31.235, 5);

        assertEquals(200, response.statusCode(), response.body());

        List<Map<String, Object>> body = parseList(response.body());

        assertTrue(
                containsDriver(body, nearDriver),
                "TC279 failed: nearby driver should be included"
        );

        assertFalse(
                containsDriver(body, farDriver),
                "TC279 failed: far driver should be excluded"
        );
    }

    // =====================================================
    // TC280
    // Nearby with radiusKm=-1 returns 400
    // =====================================================
    @Test
    @Order(280)
    void TC280_nearbyWithNegativeRadiusReturns400() throws Exception {
        HttpResponse<String> response = getNearby(30.044, 31.235, -1);

        assertEquals(400, response.statusCode(), response.body());
    }

    // =====================================================
    // TC295
    // Nearby with radiusKm=0 returns empty list
    // =====================================================
    @Test
    @Order(295)
    void TC295_nearbyWithZeroRadiusReturnsEmptyList() throws Exception {
        long driverId = seedDriver("TC295_D1");

        seedLocation(driverId, 30.05, 31.24, 30.0);

        HttpResponse<String> response = getNearby(30.044, 31.235, 0);

        assertEquals(200, response.statusCode(), response.body());

        List<Map<String, Object>> body = parseList(response.body());

        assertEquals(0, body.size(), "TC295 failed: zero radius should return empty list");
    }

    // =====================================================
    // TC353
    // Nearby DTO includes distanceKm field
    // =====================================================
    @Test
    @Order(353)
    void TC353_nearbyDtoIncludesDistanceKmField() throws Exception {
        long driverId = seedDriver("TC353_D1");

        seedLocation(driverId, 30.045, 31.236, 25.0);

        HttpResponse<String> response = getNearby(30.044, 31.235, 10);

        assertEquals(200, response.statusCode(), response.body());

        List<Map<String, Object>> body = parseList(response.body());

        assertFalse(body.isEmpty(), "TC353 setup failed: nearby list should not be empty");

        Map<String, Object> first = body.get(0);

        assertTrue(
                first.containsKey("distanceKm")
                        || first.containsKey("distance_km")
                        || first.containsKey("distance"),
                "TC353 failed: nearby DTO should include distanceKm/distance_km/distance. Actual: " + first
        );
    }

    // =====================================================
    // TC402 partial
    // DP-4 Builder: S4-F3/S4-F8/S4-F9 DTOs have Builder
    // =====================================================
    @Test
    @Order(402)
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

        assertNotNull(builder, className + " builder() returned null");

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

    private static HttpResponse<String> getNearby(double lat, double lon, double radiusKm) throws Exception {
        String url = LOCATION_SERVICE_URL
                + "/api/locations/nearby?lat=" + lat
                + "&lon=" + lon
                + "&radiusKm=" + radiusKm;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

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

        if (accessToken != null) return removeBearerPrefix(accessToken.toString());
        if (token != null) return removeBearerPrefix(token.toString());
        if (jwt != null) return removeBearerPrefix(jwt.toString());
        if (bearerToken != null) return removeBearerPrefix(bearerToken.toString());

        throw new IllegalStateException("Could not find token field in login response: " + response);
    }

    private static String removeBearerPrefix(String value) {
        if (value.startsWith("Bearer ")) {
            return value.substring("Bearer ".length());
        }

        return value;
    }

    // =====================================================
    // DB helpers
    // =====================================================

    private static long seedDriver(String label) throws Exception {
        String unique = label + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

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

    private static void seedLocation(
            long driverId,
            double latitude,
            double longitude,
            double speed
    ) throws Exception {
        String sql = """
                INSERT INTO locations
                (driver_id, latitude, longitude, timestamp, metadata)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CAST(? AS jsonb))
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
            ps.setString(4, metadata);

            ps.executeUpdate();
        }
    }

    private static void cleanTestLocationsAndDrivers() throws Exception {
        try (Connection connection = getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM locations
                    WHERE driver_id IN (
                        SELECT id FROM drivers
                        WHERE name LIKE 'TC279%'
                           OR name LIKE 'TC280%'
                           OR name LIKE 'TC295%'
                           OR name LIKE 'TC353%'
                    )
                    """)) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM drivers
                    WHERE name LIKE 'TC279%'
                       OR name LIKE 'TC280%'
                       OR name LIKE 'TC295%'
                       OR name LIKE 'TC353%'
                    """)) {
                ps.executeUpdate();
            }
        }
    }

    private static Connection getConnection() throws Exception {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // =====================================================
    // Redis helper
    // =====================================================

    private static void flushRedisCache() {
        try (Socket socket = new Socket("localhost", 6379);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            writer.write("*2\r\n$4\r\nAUTH\r\n$9\r\nredispass\r\n");
            writer.flush();
            reader.readLine();

            writer.write("*1\r\n$8\r\nFLUSHALL\r\n");
            writer.flush();
            reader.readLine();

        } catch (Exception e) {
            System.out.println("Could not flush Redis cache during test: " + e.getMessage());
        }
    }

    // =====================================================
    // Parsing helpers
    // =====================================================

    private static List<Map<String, Object>> parseList(String body) throws Exception {
        return objectMapper.readValue(
                body,
                new TypeReference<>() {}
        );
    }

    private static boolean containsDriver(List<Map<String, Object>> rows, long driverId) {
        return rows.stream().anyMatch(row -> {
            Object value = row.get("driverId");

            if (value == null) value = row.get("driver_id");
            if (value == null) value = row.get("id");

            if (value == null) return false;

            return Long.parseLong(value.toString()) == driverId;
        });
    }
}