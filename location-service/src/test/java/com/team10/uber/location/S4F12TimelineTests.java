package com.team10.uber.location;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F12TimelineTests {

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
    void clearBeforeEach() {
        flushRedisCache();
    }

    // =====================================================
    // TC128
    // Timeline returns all events, most recent first
    // =====================================================
    @Test
    @Order(128)
    void TC128_timelineReturnsAllEventsMostRecentFirst() throws Exception {
        long driverId = seedDriver("TC128_D1");

        postTrackingWithAuth(driverId, trackingBody(30.01, 31.01, 10.0, "TC128 first timeline event"));
        Thread.sleep(50);

        postTrackingWithAuth(driverId, trackingBody(30.02, 31.02, 20.0, "TC128 second timeline event"));
        Thread.sleep(50);

        postTrackingWithAuth(driverId, trackingBody(30.03, 31.03, 30.0, "TC128 third timeline event"));

        HttpResponse<String> response = getTimelineWithAuth(driverId);

        assertTrue(is2xx(response.statusCode()), response.body());

        List<Map<String, Object>> timeline = parseTimeline(response.body());

        assertTrue(timeline.size() >= 3, "TC128 failed: timeline should contain at least 3 events");

        Map<String, Object> first = timeline.get(0);
        assertTrue(
                valueEqualsAny(first, "notes", "TC128 third timeline event")
                        || doubleValue(first.get("speed")) == 30.0,
                "TC128 failed: most recent event should appear first"
        );
    }

    // =====================================================
    // TC129
    // Timeline with startTime/endTime returns only matching events
    // =====================================================
    @Test
    @Order(129)
    void TC129_timelineWithStartTimeEndTimeReturnsMatchingEvents() throws Exception {
        long driverId = seedDriver("TC129_D1");

        postTrackingWithAuth(driverId, trackingBody(30.04, 31.23, 40.0, "TC129 time range event"));

        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        HttpResponse<String> response = getTimelineWithAuth(
                driverId,
                "?startTime=" + past + "&endTime=" + future
        );

        assertTrue(is2xx(response.statusCode()), response.body());

        List<Map<String, Object>> timeline = parseTimeline(response.body());

        assertTrue(timeline.size() >= 1, "TC129 failed: timeline should include event inside time window");
    }

    // =====================================================
    // TC130
    // Timeline for non-existent driver returns 404
    // =====================================================
    @Test
    @Order(130)
    void TC130_timelineForNonExistentDriverReturns404() throws Exception {
        HttpResponse<String> response = getTimelineWithAuth(999999L);

        assertEquals(404, response.statusCode(), response.body());
    }

    // =====================================================
    // TC131
    // Timeline without Authorization header returns 401
    // =====================================================
    @Test
    @Order(131)
    void TC131_timelineWithoutAuthorizationReturns401() throws Exception {
        long driverId = seedDriver("TC131_D1");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/" + driverId + "/tracking"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(), response.body());
    }

    // =====================================================
    // TC132
    // Timeline with malformed JWT returns 401
    // =====================================================
    @Test
    @Order(132)
    void TC132_timelineWithMalformedJwtReturns401() throws Exception {
        long driverId = seedDriver("TC132_D1");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/" + driverId + "/tracking"))
                .header("Authorization", "Bearer xxx.yyy.zzz")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(), response.body());
    }

    // =====================================================
    // TC133
    // Timeline for driver with no events returns empty list
    // =====================================================
    @Test
    @Order(133)
    void TC133_timelineForDriverWithNoEventsReturnsEmptyList() throws Exception {
        long driverId = seedDriver("TC133_D1");

        HttpResponse<String> response = getTimelineWithAuth(driverId);

        assertTrue(is2xx(response.statusCode()), response.body());

        List<Map<String, Object>> timeline = parseTimeline(response.body());

        assertEquals(0, timeline.size(), "TC133 failed: driver with no tracking events should return empty list");
    }

    // =====================================================
    // TC134
    // Two identical timeline requests return identical bodies cached
    // =====================================================
    @Test
    @Order(134)
    void TC134_twoIdenticalTimelineRequestsReturnIdenticalBodies() throws Exception {
        long driverId = seedDriver("TC134_D1");

        postTrackingWithAuth(driverId, trackingBody(30.04, 31.23, 40.0, "TC134 cached event"));

        HttpResponse<String> first = getTimelineWithAuth(driverId);
        HttpResponse<String> second = getTimelineWithAuth(driverId);

        assertTrue(is2xx(first.statusCode()), first.body());
        assertTrue(is2xx(second.statusCode()), second.body());

        assertEquals(
                first.body(),
                second.body(),
                "TC134 failed: identical timeline requests should return identical cached body"
        );
    }

    // =====================================================
    // TC135
    // Each event includes timestamp/latitude/longitude/speed
    // =====================================================
    @Test
    @Order(135)
    void TC135_timelineEventIncludesTimestampLatitudeLongitudeSpeed() throws Exception {
        long driverId = seedDriver("TC135_D1");

        postTrackingWithAuth(driverId, """
                {
                  "latitude": 30.0444,
                  "longitude": 31.2357,
                  "speed": 45.0,
                  "heading": 180.0,
                  "accuracy": 5.0,
                  "notes": "TC135 full body event"
                }
                """);

        HttpResponse<String> response = getTimelineWithAuth(driverId);

        assertTrue(is2xx(response.statusCode()), response.body());

        List<Map<String, Object>> timeline = parseTimeline(response.body());

        assertTrue(timeline.size() >= 1, "TC135 failed: timeline should contain at least one event");

        Map<String, Object> event = timeline.get(0);

        assertTrue(
                hasAnyKey(event, "timestamp", "eventTime", "recordedAt", "lastUpdated", "time"),
                "TC135 failed: event should include timestamp/eventTime/recordedAt field. Actual: " + event
        );

        assertTrue(
                hasAnyKey(event, "latitude", "lat"),
                "TC135 failed: event should include latitude/lat field. Actual: " + event
        );

        assertTrue(
                hasAnyKey(event, "longitude", "lon"),
                "TC135 failed: event should include longitude/lon field. Actual: " + event
        );

        assertTrue(
                hasAnyKey(event, "speed"),
                "TC135 failed: event should include speed field. Actual: " + event
        );
    }

    // =====================================================
    // HTTP helpers
    // =====================================================

    private static HttpResponse<String> postTrackingWithAuth(long driverId, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/" + driverId + "/tracking"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> getTimelineWithAuth(long driverId) throws Exception {
        return getTimelineWithAuth(driverId, "");
    }

    private static HttpResponse<String> getTimelineWithAuth(long driverId, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/" + driverId + "/tracking" + query))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String trackingBody(double latitude, double longitude, double speed, String notes) {
        return """
                {
                  "latitude": %s,
                  "longitude": %s,
                  "speed": %s,
                  "heading": 100.0,
                  "accuracy": 5.0,
                  "notes": "%s"
                }
                """.formatted(latitude, longitude, speed, notes);
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
    // Response parsing helpers
    // =====================================================

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseTimeline(String body) throws Exception {
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

        fail("Timeline response should be an array or a paginated object with content. Actual body: " + body);
        return List.of();
    }

    private static boolean hasAnyKey(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return true;
            }
        }

        return false;
    }

    private static boolean valueEqualsAny(Map<String, Object> map, String key, String expectedValue) {
        Object value = map.get(key);

        if (value == null) {
            return false;
        }

        return expectedValue.equals(value.toString());
    }

    private static double doubleValue(Object value) {
        if (value == null) {
            return -1;
        }

        return Double.parseDouble(value.toString());
    }
}