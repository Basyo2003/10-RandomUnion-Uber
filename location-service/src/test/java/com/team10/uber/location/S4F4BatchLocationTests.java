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
class S4F4BatchLocationTests {

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
    // TC281
    // POST batch creates multiple Location rows for one driver
    // =====================================================
    @Test
    @Order(281)
    void TC281_postBatchCreatesMultipleLocationRowsForOneDriver() throws Exception {
        long driverId = seedDriver("TC281_D1");

        String body = """
                {
                  "driverId": %d,
                  "locations": [
                    {
                      "latitude": 30.01,
                      "longitude": 31.01,
                      "metadata": {
                        "speed": 10,
                        "heading": 90
                      }
                    },
                    {
                      "latitude": 30.02,
                      "longitude": 31.02,
                      "metadata": {
                        "speed": 20,
                        "heading": 100
                      }
                    },
                    {
                      "latitude": 30.03,
                      "longitude": 31.03,
                      "metadata": {
                        "speed": 30,
                        "heading": 110
                      }
                    }
                  ]
                }
                """.formatted(driverId);

        HttpResponse<String> response = postBatchWithAuth(body);

        assertTrue(is2xx(response.statusCode()), response.body());

        long count = countLocationsForDriver(driverId);

        assertTrue(
                count >= 3,
                "TC281 failed: expected at least 3 location rows for driverId=" + driverId + ", got " + count
        );
    }

    // =====================================================
    // TC282
    // Batch with empty locations array returns 400
    // =====================================================
    @Test
    @Order(282)
    void TC282_batchWithEmptyLocationsArrayReturns400() throws Exception {
        long driverId = seedDriver("TC282_D1");

        String body = """
                {
                  "driverId": %d,
                  "locations": []
                }
                """.formatted(driverId);

        HttpResponse<String> response = postBatchWithAuth(body);

        assertEquals(400, response.statusCode(), response.body());
    }

    // =====================================================
    // TC356
    // Batch with unknown driverId returns 404
    // =====================================================
    @Test
    @Order(356)
    void TC356_batchWithUnknownDriverIdReturns404() throws Exception {
        String body = """
                {
                  "driverId": 999999,
                  "locations": [
                    {
                      "latitude": 30.01,
                      "longitude": 31.01,
                      "metadata": {
                        "speed": 10,
                        "heading": 90
                      }
                    }
                  ]
                }
                """;

        HttpResponse<String> response = postBatchWithAuth(body);

        assertEquals(404, response.statusCode(), response.body());
    }

    // =====================================================
    // HTTP helpers
    // =====================================================

    private static HttpResponse<String> postBatchWithAuth(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/batch"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static boolean is2xx(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
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

    private static long countLocationsForDriver(long driverId) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM locations
                WHERE driver_id = ?
                """;

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, driverId);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private static void cleanTestLocationsAndDrivers() throws Exception {
        try (Connection connection = getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM locations
                    WHERE driver_id IN (
                        SELECT id FROM drivers
                        WHERE name LIKE 'TC281%'
                           OR name LIKE 'TC282%'
                           OR name LIKE 'TC356%'
                    )
                    """)) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM drivers
                    WHERE name LIKE 'TC281%'
                       OR name LIKE 'TC282%'
                       OR name LIKE 'TC356%'
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
}