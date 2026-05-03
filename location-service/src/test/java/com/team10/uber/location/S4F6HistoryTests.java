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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F6HistoryTests {

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
    // TC286
    // History returns rows in date range for given driver
    // =====================================================
    @Test
    @Order(286)
    void TC286_historyReturnsRowsInDateRangeForGivenDriver() throws Exception {
        long driverId = seedDriver("TC286_D1");

        long march10 = seedLocation(driverId, "2026-03-10 10:00:00", 30.01, 31.01, 20.0);
        long march15 = seedLocation(driverId, "2026-03-15 10:00:00", 30.02, 31.02, 30.0);
        long april15 = seedLocation(driverId, "2026-04-15 10:00:00", 30.03, 31.03, 40.0);

        HttpResponse<String> response = getHistory(
                "2026-03-01",
                "2026-03-31",
                driverId
        );

        assertEquals(200, response.statusCode(), response.body());

        List<Map<String, Object>> body = parseListOrPageContent(response.body());

        assertTrue(
                body.size() >= 2,
                "TC286 failed: expected at least two March rows"
        );

        assertTrue(
                containsLocation(body, march10),
                "TC286 failed: March 10 row should be included"
        );

        assertTrue(
                containsLocation(body, march15),
                "TC286 failed: March 15 row should be included"
        );

        assertFalse(
                containsLocation(body, april15),
                "TC286 failed: April row should be excluded"
        );
    }

    // =====================================================
    // TC287
    // History with no rows in range returns empty list
    // =====================================================
    @Test
    @Order(287)
    void TC287_historyWithNoRowsInRangeReturnsEmptyList() throws Exception {
        long driverId = seedDriver("TC287_D1");

        HttpResponse<String> response = getHistory(
                "2099-01-01",
                "2099-01-31",
                driverId
        );

        assertEquals(200, response.statusCode(), response.body());

        List<Map<String, Object>> body = parseListOrPageContent(response.body());

        assertEquals(
                0,
                body.size(),
                "TC287 failed: empty date range should return empty list"
        );
    }

    // =====================================================
    // TC357
    // History without driverId param returns 200 or 400, not 500
    // =====================================================
    @Test
    @Order(357)
    void TC357_historyWithoutDriverIdReturnsGracefulStatus() throws Exception {
        long d1 = seedDriver("TC357_A");
        long d2 = seedDriver("TC357_B");

        seedLocation(d1, "2026-03-10 10:00:00", 30.01, 31.01, 20.0);
        seedLocation(d2, "2026-03-15 10:00:00", 30.02, 31.02, 30.0);

        HttpResponse<String> response = getHistoryWithoutDriverId(
                "2026-03-01",
                "2026-03-31"
        );

        assertTrue(
                response.statusCode() == 200 || response.statusCode() == 400,
                "TC357 failed: missing driverId should return 200 or 400, not "
                        + response.statusCode()
                        + ". Body: "
                        + response.body()
        );
    }

    // =====================================================
    // TC402 partial
    // S4-F6 has no response DTO, so Builder is NOT required here.
    // We still verify the location-service M1 DTOs that actually have DTOs.
    // =====================================================
    @Test
    @Order(402)
    void TC402_locationServiceM1DtosHaveBuilderButS4F6IsSkipped() throws Exception {
        assertDtoHasBuilder("com.team10.uber.location.dto.NearbyDriverDTO");
        assertDtoHasBuilder("com.team10.uber.location.dto.DriverMovementSummaryDTO");
        assertDtoHasBuilder("com.team10.uber.location.dto.StationaryDriverDTO");
    }

    private static void assertDtoHasBuilder(String className) throws Exception {
        Class<?> dtoClass = Class.forName(className);

        var builderMethod = dtoClass.getDeclaredMethod("builder");

        assertTrue(
                java.lang.reflect.Modifier.isStatic(builderMethod.getModifiers()),
                className + " builder() method must be static"
        );

        Object builder = builderMethod.invoke(null);

        assertNotNull(builder, className + " builder() returned null");

        var buildMethod = builder.getClass().getDeclaredMethod("build");
        Object dto = buildMethod.invoke(builder);

        assertTrue(
                dtoClass.isInstance(dto),
                className + " build() must return the DTO type"
        );
    }

    // =====================================================
    // HTTP helpers
    // =====================================================

    private static HttpResponse<String> getHistory(String startDate, String endDate, long driverId) throws Exception {
        String url = LOCATION_SERVICE_URL
                + "/api/locations/history"
                + "?startDate=" + startDate
                + "&endDate=" + endDate
                + "&driverId=" + driverId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> getHistoryWithoutDriverId(String startDate, String endDate) throws Exception {
        String url = LOCATION_SERVICE_URL
                + "/api/locations/history"
                + "?startDate=" + startDate
                + "&endDate=" + endDate;

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
            String timestamp,
            double latitude,
            double longitude,
            double speed
    ) throws Exception {
        LocalDateTime dateTime = LocalDateTime.parse(timestamp.replace(" ", "T"));

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
            ps.setTimestamp(4, Timestamp.valueOf(dateTime));
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
                        WHERE name LIKE 'TC286%'
                           OR name LIKE 'TC287%'
                           OR name LIKE 'TC357%'
                    )
                    """)) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM drivers
                    WHERE name LIKE 'TC286%'
                       OR name LIKE 'TC287%'
                       OR name LIKE 'TC357%'
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseListOrPageContent(String body) throws Exception {
        Object parsed = objectMapper.readValue(body, Object.class);

        if (parsed instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }

        if (parsed instanceof Map<?, ?> map) {
            Object content = map.get("content");

            if (content instanceof List<?> contentList) {
                return (List<Map<String, Object>>) contentList;
            }
        }

        fail("History response should be an array or page object with content. Actual: " + body);
        return List.of();
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