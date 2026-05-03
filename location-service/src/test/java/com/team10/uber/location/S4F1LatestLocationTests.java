package com.team10.uber.location;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F1LatestLocationTests {

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
    // TC275
    // Latest returns the most-recent timestamp's location row
    // =====================================================
    @Test
    @Order(275)
    void TC275_latestReturnsMostRecentTimestampLocationRow() throws Exception {
        long driverId = seedDriver("TC275_D1");

        long oldLocationId = seedLocation(
                driverId,
                "2026-01-15 08:00:00",
                30.01,
                31.01,
                20.0
        );

        long latestLocationId = seedLocation(
                driverId,
                "2026-01-15 18:00:00",
                30.02,
                31.02,
                40.0
        );

        HttpResponse<String> response = getLatestWithAuth(driverId);

        assertEquals(200, response.statusCode(), response.body());

        Map<String, Object> body = objectMapper.readValue(
                response.body(),
                new TypeReference<>() {}
        );

        long returnedId = longValue(body.get("id"));

        assertNotEquals(
                oldLocationId,
                returnedId,
                "TC275 failed: latest endpoint returned the older 08:00 location"
        );

        assertEquals(
                latestLocationId,
                returnedId,
                "TC275 failed: latest endpoint must return the 18:00 most-recent location"
        );
    }

    // =====================================================
    // TC276
    // Latest for driver with zero locations returns 404
    // =====================================================
    @Test
    @Order(276)
    void TC276_latestForDriverWithZeroLocationsReturns404() throws Exception {
        long driverId = seedDriver("TC276_D1");

        HttpResponse<String> response = getLatestWithAuth(driverId);

        assertEquals(404, response.statusCode(), response.body());
    }

    // =====================================================
    // TC296
    // Latest for non-existent driver returns 404
    // =====================================================
    @Test
    @Order(296)
    void TC296_latestForNonExistentDriverReturns404() throws Exception {
        HttpResponse<String> response = getLatestWithAuth(999999L);

        assertEquals(404, response.statusCode(), response.body());
    }

    // =====================================================
    // HTTP helpers
    // =====================================================

    private static HttpResponse<String> getLatestWithAuth(long driverId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/driver/" + driverId + "/latest"))
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

    private static long seedLocation(
            long driverId,
            String timestamp,
            double latitude,
            double longitude,
            double speed
    ) throws Exception {
        String sql = """
                INSERT INTO locations
                (driver_id, latitude, longitude, timestamp, metadata)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                RETURNING id
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
            ps.setTimestamp(4, Timestamp.valueOf(timestamp));
            ps.setString(5, metadata);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong("id");
            }
        }
    }

    private static void cleanTestLocationsAndDrivers() throws Exception {
        try (Connection connection = getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM locations
                    WHERE driver_id IN (
                        SELECT id FROM drivers
                        WHERE name LIKE 'TC275%'
                           OR name LIKE 'TC276%'
                           OR name LIKE 'TC296%'
                    )
                    """)) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM drivers
                    WHERE name LIKE 'TC275%'
                       OR name LIKE 'TC276%'
                       OR name LIKE 'TC296%'
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

    private static long longValue(Object value) {
        assertNotNull(value, "Expected numeric value but got null");
        return Long.parseLong(value.toString());
    }
}