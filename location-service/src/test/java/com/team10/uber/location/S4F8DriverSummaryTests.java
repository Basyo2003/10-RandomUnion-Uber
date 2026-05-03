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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F8DriverSummaryTests {

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
    // TC290
    // Summary returns totalLocationPoints and lastTimestamp
    // =====================================================
    @Test
    @Order(290)
    void TC290_summaryReturnsTotalLocationPoints() throws Exception {
        long driverId = seedDriver("TC290_D1");

        seedLocation(driverId, "2026-03-10 10:00:00", 40.0);
        seedLocation(driverId, "2026-03-15 10:00:00", 50.0);
        seedLocation(driverId, "2026-03-20 10:00:00", 60.0);

        HttpResponse<String> response = getSummary(
                driverId,
                "2026-03-01",
                "2026-03-31"
        );

        assertEquals(200, response.statusCode(), response.body());

        Map<String, Object> body = parseMap(response.body());

        assertEquals(
                3L,
                longValue(body.get("totalLocationPoints")),
                "TC290 failed: totalLocationPoints should equal exactly 3"
        );

        assertTrue(
                body.containsKey("lastTimestamp") || body.containsKey("lastUpdated") || body.containsKey("last_location_time"),
                "TC290 failed: summary should include lastTimestamp/lastUpdated field. Actual: " + body
        );
    }

    // =====================================================
    // TC291
    // Summary for driver with no locations returns totalPoints=0 or 404
    // =====================================================
    @Test
    @Order(291)
    void TC291_summaryForDriverWithNoLocationsReturns200Or404() throws Exception {
        long driverId = seedDriver("TC291_D1");

        HttpResponse<String> response = getSummary(
                driverId,
                "2026-03-01",
                "2026-03-31"
        );

        assertTrue(
                response.statusCode() == 200 || response.statusCode() == 404,
                "TC291 failed: expected 200 or 404, got "
                        + response.statusCode()
                        + ". Body: "
                        + response.body()
        );

        if (response.statusCode() == 200) {
            Map<String, Object> body = parseMap(response.body());

            assertEquals(
                    0L,
                    longValue(body.get("totalLocationPoints")),
                    "TC291 failed: empty driver summary should have totalLocationPoints=0"
            );
        }
    }

    // =====================================================
    // TC297
    // Summary with startDate > endDate returns 400
    // =====================================================
    @Test
    @Order(297)
    void TC297_summaryWithStartDateAfterEndDateReturns400() throws Exception {
        long driverId = seedDriver("TC297_D1");

        HttpResponse<String> response = getSummary(
                driverId,
                "2026-12-31",
                "2026-01-01"
        );

        assertEquals(400, response.statusCode(), response.body());
    }

    // =====================================================
    // TC354
    // Driver summary averageSpeed = mean of metadata.speed values
    // =====================================================
    @Test
    @Order(354)
    void TC354_driverSummaryAverageSpeedEqualsMeanOfMetadataSpeedValues() throws Exception {
        long driverId = seedDriver("TC354_D1");

        seedLocation(driverId, "2026-03-10 10:00:00", 40.0);
        seedLocation(driverId, "2026-03-15 10:00:00", 60.0);
        seedLocation(driverId, "2026-03-20 10:00:00", 80.0);

        HttpResponse<String> response = getSummary(
                driverId,
                "2026-03-01",
                "2026-03-31"
        );

        assertEquals(200, response.statusCode(), response.body());

        Map<String, Object> body = parseMap(response.body());

        assertEquals(
                60.0,
                doubleValue(body.get("averageSpeed")),
                0.5,
                "TC354 failed: averageSpeed should be 60.0"
        );
    }

    // =====================================================
    // TC355
    // Driver summary maxSpeed = max of metadata.speed values
    // =====================================================
    @Test
    @Order(355)
    void TC355_driverSummaryMaxSpeedEqualsMaxOfMetadataSpeedValues() throws Exception {
        long driverId = seedDriver("TC355_D1");

        seedLocation(driverId, "2026-03-10 10:00:00", 30.0);
        seedLocation(driverId, "2026-03-15 10:00:00", 90.0);

        HttpResponse<String> response = getSummary(
                driverId,
                "2026-03-01",
                "2026-03-31"
        );

        assertEquals(200, response.statusCode(), response.body());

        Map<String, Object> body = parseMap(response.body());

        assertEquals(
                90.0,
                doubleValue(body.get("maxSpeed")),
                0.5,
                "TC355 failed: maxSpeed should be 90.0"
        );
    }

    // =====================================================
    // TC402 partial
    // DP-4 Builder: Location-service M1 DTOs have Builder
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

    private static HttpResponse<String> getSummary(
            long driverId,
            String startDate,
            String endDate
    ) throws Exception {
        String url = LOCATION_SERVICE_URL
                + "/api/locations/driver/" + driverId + "/summary"
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
            double speed
    ) throws Exception {
        LocalDateTime dateTime = LocalDateTime.parse(timestamp.replace(" ", "T"));

        String sql = """
                INSERT INTO locations
                (driver_id, latitude, longitude, timestamp, metadata)
                VALUES (?, 30.01, 31.01, ?, CAST(? AS jsonb))
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
            ps.setTimestamp(2, Timestamp.valueOf(dateTime));
            ps.setString(3, metadata);

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
                        WHERE name LIKE 'TC290%'
                           OR name LIKE 'TC291%'
                           OR name LIKE 'TC297%'
                           OR name LIKE 'TC354%'
                           OR name LIKE 'TC355%'
                    )
                    """)) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM drivers
                    WHERE name LIKE 'TC290%'
                       OR name LIKE 'TC291%'
                       OR name LIKE 'TC297%'
                       OR name LIKE 'TC354%'
                       OR name LIKE 'TC355%'
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

    private static Map<String, Object> parseMap(String body) throws Exception {
        return objectMapper.readValue(
                body,
                new TypeReference<>() {}
        );
    }

    private static long longValue(Object value) {
        assertNotNull(value, "Expected numeric value but got null");
        return Long.parseLong(value.toString());
    }

    private static double doubleValue(Object value) {
        assertNotNull(value, "Expected decimal value but got null");
        return Double.parseDouble(value.toString());
    }
}