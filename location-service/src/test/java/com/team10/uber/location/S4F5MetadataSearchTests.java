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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F5MetadataSearchTests {

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
    // TC283
    // Metadata search operator=eq matches exact metadata value
    // =====================================================
    @Test
    @Order(283)
    void TC283_metadataSearchEqMatchesExactValue() throws Exception {
        long driverId = seedDriver("TC283_D1");

        long speed50Id = seedLocation(driverId, 30.01, 31.01, 50.0);
        long speed80Id = seedLocation(driverId, 30.02, 31.02, 80.0);

        HttpResponse<String> response = getMetadataSearch("speed", "eq", "50");

        assertEquals(200, response.statusCode(), response.body());

        List<Map<String, Object>> body = parseList(response.body());

        assertTrue(
                body.size() >= 1,
                "TC283 failed: eq search should return at least one speed=50 row"
        );

        assertTrue(
                containsLocation(body, speed50Id),
                "TC283 failed: eq search should include speed=50 location"
        );

        assertFalse(
                containsLocation(body, speed80Id),
                "TC283 failed: eq search should exclude speed=80 location"
        );
    }

    // =====================================================
    // TC284
    // Metadata search operator=gt value=60 returns only rows with speed > 60
    // =====================================================
    @Test
    @Order(284)
    void TC284_metadataSearchGtReturnsOnlyRowsAboveThreshold() throws Exception {
        long driverId = seedDriver("TC284_D1");

        long speed50Id = seedLocation(driverId, 30.01, 31.01, 50.0);
        long speed80Id = seedLocation(driverId, 30.02, 31.02, 80.0);

        HttpResponse<String> response = getMetadataSearch("speed", "gt", "60");

        assertEquals(200, response.statusCode(), response.body());

        List<Map<String, Object>> body = parseList(response.body());

        assertTrue(
                containsLocation(body, speed80Id),
                "TC284 failed: gt search should include speed=80 location"
        );

        assertFalse(
                containsLocation(body, speed50Id),
                "TC284 failed: gt search should exclude speed=50 location"
        );
    }

    // =====================================================
    // TC285
    // Metadata search with operator=foo returns 400
    // =====================================================
    @Test
    @Order(285)
    void TC285_metadataSearchWithInvalidOperatorReturns400() throws Exception {
        HttpResponse<String> response = getMetadataSearch("speed", "foo", "50");

        assertEquals(400, response.statusCode(), response.body());
    }

    // =====================================================
    // TC374
    // Metadata search operator=lt value=40 returns rows below threshold
    // =====================================================
    @Test
    @Order(374)
    void TC374_metadataSearchLtReturnsRowsBelowThreshold() throws Exception {
        long driverId = seedDriver("TC374_D1");

        long speed20Id = seedLocation(driverId, 30.01, 31.01, 20.0);
        long speed80Id = seedLocation(driverId, 30.02, 31.02, 80.0);

        HttpResponse<String> response = getMetadataSearch("speed", "lt", "40");

        assertEquals(200, response.statusCode(), response.body());

        List<Map<String, Object>> body = parseList(response.body());

        assertTrue(
                containsLocation(body, speed20Id),
                "TC374 failed: lt search should include speed=20 location"
        );

        assertFalse(
                containsLocation(body, speed80Id),
                "TC374 failed: lt search should exclude speed=80 location"
        );
    }

    // =====================================================
    // HTTP helpers
    // =====================================================

    private static HttpResponse<String> getMetadataSearch(String key, String operator, String value) throws Exception {
        String url = LOCATION_SERVICE_URL
                + "/api/locations/metadata/search"
                + "?key=" + key
                + "&operator=" + operator
                + "&value=" + value;

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

    private static long seedLocation(
            long driverId,
            double latitude,
            double longitude,
            double speed
    ) throws Exception {
        String sql = """
                INSERT INTO locations
                (driver_id, latitude, longitude, timestamp, metadata)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CAST(? AS jsonb))
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
            ps.setString(4, metadata);

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
                        WHERE name LIKE 'TC283%'
                           OR name LIKE 'TC284%'
                           OR name LIKE 'TC285%'
                           OR name LIKE 'TC374%'
                    )
                    """)) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM drivers
                    WHERE name LIKE 'TC283%'
                       OR name LIKE 'TC284%'
                       OR name LIKE 'TC285%'
                       OR name LIKE 'TC374%'
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

    private static boolean containsLocation(List<Map<String, Object>> rows, long locationId) {
        return rows.stream().anyMatch(row -> {
            Object value = row.get("id");

            if (value == null) value = row.get("locationId");
            if (value == null) value = row.get("location_id");

            if (value == null) return false;

            return Long.parseLong(value.toString()) == locationId;
        });
    }
}