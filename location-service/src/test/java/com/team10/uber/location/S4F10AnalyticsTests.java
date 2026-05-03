package com.team10.uber.location;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F10AnalyticsTests {

    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final String LOCATION_SERVICE_URL = "http://localhost:8084";

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/uberdb";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    private static final String MONGO_URI = "mongodb://root:rootpass@localhost:27017/ubermongo?authSource=admin";

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
    void resetBeforeEachTest() throws Exception {
        flushRedisCache();
        cleanTestLocationsAndDrivers();
    }

    @Test
    @Order(100)
    void TC100_dashboardReturnsRequiredAnalyticsFields() throws Exception {
        cleanDateRange("2026-04-01", "2026-04-30");

        long d1 = seedDriver("TC100_D1");
        long d2 = seedDriver("TC100_D2");
        long d3 = seedDriver("TC100_D3");

        insertLocation(d1, "2026-04-05 08:00:00", 20);
        insertLocation(d1, "2026-04-05 08:15:00", 30);
        insertLocation(d1, "2026-04-05 17:00:00", 40);

        insertLocation(d2, "2026-04-06 08:00:00", 50);
        insertLocation(d2, "2026-04-06 17:00:00", 60);

        insertLocation(d3, "2026-04-07 17:00:00", 70);
        insertLocation(d3, "2026-04-07 08:00:00", 80);
        insertLocation(d3, "2026-04-07 17:30:00", 90);

        Map<String, Object> response = getAnalytics("2026-04-01", "2026-04-30");

        assertEquals(8L, longValue(response.get("totalLocationEvents")));
        assertEquals(3L, longValue(response.get("activeDrivers")));
        assertTrue(response.containsKey("averageSpeed"), "averageSpeed field must exist");

        Map<String, Object> eventsByHour = mapValue(response.get("eventsByHour"));
        assertNotNull(eventsByHour);
        assertTrue(hasHour(eventsByHour, "8", "08"), "eventsByHour must contain hour 8/08");
        assertTrue(hasHour(eventsByHour, "17"), "eventsByHour must contain hour 17");
    }

    @Test
    @Order(101)
    void TC101_totalLocationEventsEqualsExactCount() throws Exception {
        cleanDateRange("2026-09-01", "2026-09-30");

        long d1 = seedDriver("TC101_D1");

        for (int i = 0; i < 7; i++) {
            insertLocation(d1, "2026-09-15 12:00:00", 40);
        }

        Map<String, Object> response = getAnalytics("2026-09-01", "2026-09-30");

        assertEquals(7L, longValue(response.get("totalLocationEvents")));
    }

    @Test
    @Order(102)
    void TC102_activeDriversCountsDistinctDriverIds() throws Exception {
        cleanDateRange("2026-09-01", "2026-09-30");

        long d1 = seedDriver("TC102_D1");
        long d2 = seedDriver("TC102_D2");

        insertLocation(d1, "2026-09-15 10:00:00", 20);
        insertLocation(d1, "2026-09-15 11:00:00", 30);
        insertLocation(d1, "2026-09-15 12:00:00", 40);
        insertLocation(d2, "2026-09-15 13:00:00", 50);

        Map<String, Object> response = getAnalytics("2026-09-01", "2026-09-30");

        assertEquals(2L, longValue(response.get("activeDrivers")));
    }

    @Test
    @Order(103)
    void TC103_averageSpeedEqualsMeanOfMetadataSpeedValues() throws Exception {
        cleanDateRange("2026-09-01", "2026-09-30");

        long d1 = seedDriver("TC103_D1");

        insertLocation(d1, "2026-09-15 10:00:00", 30);
        insertLocation(d1, "2026-09-15 11:00:00", 50);
        insertLocation(d1, "2026-09-15 12:00:00", 70);
        insertLocation(d1, "2026-09-15 13:00:00", 90);

        Map<String, Object> response = getAnalytics("2026-09-01", "2026-09-30");

        assertEquals(60.0, doubleValue(response.get("averageSpeed")), 0.5);
    }

    @Test
    @Order(104)
    void TC104_eventsByHourGroupsCountsByHourOfDay() throws Exception {
        cleanDateRange("2026-09-01", "2026-09-30");

        long d1 = seedDriver("TC104_D1");

        insertLocation(d1, "2026-09-15 09:00:00", 30);
        insertLocation(d1, "2026-09-15 09:15:00", 40);
        insertLocation(d1, "2026-09-15 09:30:00", 50);
        insertLocation(d1, "2026-09-15 17:00:00", 60);
        insertLocation(d1, "2026-09-15 17:30:00", 70);

        Map<String, Object> response = getAnalytics("2026-09-01", "2026-09-30");
        Map<String, Object> eventsByHour = mapValue(response.get("eventsByHour"));

        assertEquals(3L, getHourCount(eventsByHour, "9", "09"));
        assertEquals(2L, getHourCount(eventsByHour, "17"));
    }

    @Test
    @Order(105)
    void TC105_dashboardWithNoEventsReturnsZeros() throws Exception {
        cleanDateRange("2099-01-01", "2099-01-31");

        Map<String, Object> response = getAnalytics("2099-01-01", "2099-01-31");

        assertEquals(0L, longValue(response.get("totalLocationEvents")));
        assertEquals(0L, longValue(response.get("activeDrivers")));
        assertEquals(0.0, doubleValue(response.get("averageSpeed")), 0.01);
    }

    @Test
    @Order(106)
    void TC106_dashboardWithStartDateAfterEndDateReturns400() throws Exception {
        HttpResponse<String> response = getWithAuth(
                LOCATION_SERVICE_URL + "/api/locations/analytics?startDate=2026-04-30&endDate=2026-04-01"
        );

        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    @Order(107)
    void TC107_dashboardWithoutAuthorizationHeaderReturns401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/analytics?startDate=2026-04-01&endDate=2026-04-30"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(), response.body());
    }

    @Test
    @Order(108)
    void TC108_dashboardWithMalformedJwtReturns401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/analytics?startDate=2026-04-01&endDate=2026-04-30"))
                .header("Authorization", "Bearer xxx.yyy.zzz")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(), response.body());
    }

    @Test
    @Order(109)
    void TC109_eventExactlyOnStartDateIsIncluded() throws Exception {
        cleanDateRange("2026-05-01", "2026-05-31");

        long d1 = seedDriver("TC109_D1");
        insertLocation(d1, "2026-05-01 00:00:00", 30);

        Map<String, Object> response = getAnalytics("2026-05-01", "2026-05-31");

        assertEquals(1L, longValue(response.get("totalLocationEvents")));
    }

    @Test
    @Order(110)
    void TC110_eventsOutsideRangeAreExcluded() throws Exception {
        cleanDateRange("2026-06-01", "2026-06-30");
        cleanDateRange("2026-08-01", "2026-08-31");

        long d1 = seedDriver("TC110_D1");

        insertLocation(d1, "2026-06-15 12:00:00", 30);
        insertLocation(d1, "2026-08-15 12:00:00", 90);

        Map<String, Object> response = getAnalytics("2026-06-01", "2026-06-30");

        assertEquals(1L, longValue(response.get("totalLocationEvents")));
    }

    @Test
    @Order(111)
    void TC111_averageSpeedZeroWhenNoEventsInRange() throws Exception {
        cleanDateRange("2099-06-01", "2099-06-30");

        Map<String, Object> response = getAnalytics("2099-06-01", "2099-06-30");

        assertEquals(0.0, doubleValue(response.get("averageSpeed")), 0.01);
    }

    @Test
    @Order(112)
    void TC112_firstDashboardCallWritesAnalyticsViewedToMongo() throws Exception {
        cleanDateRange("2026-07-01", "2026-07-31");

        long before = countAnalyticsViewedEvents();

        Map<String, Object> response = getAnalytics("2026-07-01", "2026-07-31");

        assertNotNull(response);

        long after = countAnalyticsViewedEvents();

        assertTrue(after > before, "ANALYTICS_VIEWED count should increase after dashboard call");
    }

    @Test
    @Order(113)
    void TC113_cacheHitDashboardCallStillLogsAnalyticsViewed() throws Exception {
        cleanDateRange("2026-07-01", "2026-07-31");

        getAnalytics("2026-07-01", "2026-07-31");
        long afterFirstCall = countAnalyticsViewedEvents();

        getAnalytics("2026-07-01", "2026-07-31");
        long afterSecondCall = countAnalyticsViewedEvents();

        assertTrue(afterSecondCall > afterFirstCall, "ANALYTICS_VIEWED should increase even on cache hit");
    }

    @Test
    @Order(114)
    void TC114_twoIdenticalDashboardRequestsReturnIdenticalTotals() throws Exception {
        cleanDateRange("2026-07-01", "2026-07-31");

        long d1 = seedDriver("TC114_D1");
        insertLocation(d1, "2026-07-15 12:00:00", 30);

        Map<String, Object> first = getAnalytics("2026-07-01", "2026-07-31");
        Map<String, Object> second = getAnalytics("2026-07-01", "2026-07-31");

        assertEquals(
                longValue(first.get("totalLocationEvents")),
                longValue(second.get("totalLocationEvents"))
        );
    }

    @Test
    @Order(115)
    void TC115_insertAfterFirstCallCachedBodyStillReturned() throws Exception {
        cleanDateRange("2026-11-01", "2026-11-30");

        long d1 = seedDriver("TC115_D1");

        Map<String, Object> first = getAnalytics("2026-11-01", "2026-11-30");
        long t1 = longValue(first.get("totalLocationEvents"));

        insertLocation(d1, "2026-11-15 12:00:00", 40);

        Map<String, Object> second = getAnalytics("2026-11-01", "2026-11-30");
        long t2 = longValue(second.get("totalLocationEvents"));

        assertEquals(t1, t2, "Second response should be cached and unchanged after insert");
    }

    @Test
    @Order(116)
    void TC116_eventsByHourOmitsHoursWithZeroEvents() throws Exception {
        cleanDateRange("2026-12-01", "2026-12-31");

        long d1 = seedDriver("TC116_D1");

        insertLocation(d1, "2026-12-05 10:00:00", 30);
        insertLocation(d1, "2026-12-06 14:00:00", 40);

        Map<String, Object> response = getAnalytics("2026-12-01", "2026-12-31");
        Map<String, Object> eventsByHour = mapValue(response.get("eventsByHour"));

        assertTrue(hasHour(eventsByHour, "10"), "eventsByHour should contain 10");
        assertTrue(hasHour(eventsByHour, "14"), "eventsByHour should contain 14");
        assertFalse(hasHour(eventsByHour, "3", "03"), "eventsByHour should not contain zero-count hour 3/03");
    }

    @Test
    @Order(117)
    void TC117_multipleEventsFromSameDriverCountOnceTowardActiveDrivers() throws Exception {
        cleanDateRange("2026-10-01", "2026-10-31");

        long d1 = seedDriver("TC117_D1");

        for (int i = 0; i < 10; i++) {
            insertLocation(d1, "2026-10-15 " + String.format("%02d", i) + ":00:00", 40);
        }

        Map<String, Object> response = getAnalytics("2026-10-01", "2026-10-31");

        assertEquals(10L, longValue(response.get("totalLocationEvents")));
        assertEquals(1L, longValue(response.get("activeDrivers")));
    }

    @Test
    @Order(401)
    void TC401_locationAnalyticsDtoHasWorkingBuilder() throws Exception {
        assertDtoHasFluentBuilder("com.team10.uber.location.dto.LocationAnalyticsDTO");
    }

    private static void assertDtoHasFluentBuilder(String className) throws Exception {
        Class<?> dtoClass = Class.forName(className);

        Method builderMethod = dtoClass.getDeclaredMethod("builder");

        assertTrue(
                Modifier.isStatic(builderMethod.getModifiers()),
                className + " builder() method must be static"
        );

        Object builder = builderMethod.invoke(null);

        assertNotNull(builder, className + " builder() returned null");

        Method fluentSetter = null;

        for (Method method : builder.getClass().getDeclaredMethods()) {
            if (!method.getName().equals("build") && method.getParameterCount() == 1) {
                fluentSetter = method;
                break;
            }
        }

        assertNotNull(fluentSetter, className + " Builder must have at least one fluent setter");

        Object sampleValue = sampleValueFor(fluentSetter.getParameterTypes()[0]);
        Object setterReturn = fluentSetter.invoke(builder, sampleValue);

        assertSame(
                builder,
                setterReturn,
                className + " builder setter must return the same Builder instance for fluent chaining"
        );

        Method buildMethod = builder.getClass().getDeclaredMethod("build");
        Object dto = buildMethod.invoke(builder);

        assertTrue(
                dtoClass.isInstance(dto),
                className + " build() must return the DTO type"
        );
    }

    private static Object sampleValueFor(Class<?> type) {
        if (type.equals(String.class)) return "sample";
        if (type.equals(Long.class) || type.equals(long.class)) return 1L;
        if (type.equals(Integer.class) || type.equals(int.class)) return 1;
        if (type.equals(Double.class) || type.equals(double.class)) return 1.0;
        if (type.equals(Float.class) || type.equals(float.class)) return 1.0f;
        if (Map.class.isAssignableFrom(type)) return Map.of("10", 1L);
        return null;
    }

    // =====================================================
    // HTTP helpers
    // =====================================================

    private static Map<String, Object> getAnalytics(String startDate, String endDate) throws Exception {
        HttpResponse<String> response = getWithAuth(
                LOCATION_SERVICE_URL + "/api/locations/analytics?startDate=" + startDate + "&endDate=" + endDate
        );

        assertEquals(200, response.statusCode(), response.body());

        return objectMapper.readValue(
                response.body(),
                new TypeReference<>() {}
        );
    }

    private static HttpResponse<String> getWithAuth(String url) throws Exception {
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

    private static void insertLocation(long driverId, String timestamp, double speed) throws Exception {
        LocalDateTime dateTime = LocalDateTime.parse(timestamp.replace(" ", "T"));

        String sql = """
                INSERT INTO locations
                (driver_id, latitude, longitude, timestamp, metadata)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
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
            ps.setDouble(2, 30.01);
            ps.setDouble(3, 31.01);
            ps.setTimestamp(4, Timestamp.valueOf(dateTime));
            ps.setString(5, metadata);

            ps.executeUpdate();
        }
    }

    private static void cleanDateRange(String startDate, String endDate) throws Exception {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate endExclusive = LocalDate.parse(endDate).plusDays(1);

        String sql = """
                DELETE FROM locations
                WHERE timestamp >= ?
                  AND timestamp < ?
                """;

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(start.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(endExclusive.atStartOfDay()));
            ps.executeUpdate();
        }
    }

    private static void cleanTestLocationsAndDrivers() throws Exception {
        try (Connection connection = getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM locations
                    WHERE driver_id IN (
                        SELECT id FROM drivers WHERE name LIKE 'TC%'
                    )
                    """)) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM drivers
                    WHERE name LIKE 'TC%'
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
    // Mongo helper
    // =====================================================

    private static long countAnalyticsViewedEvents() {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
            MongoDatabase database = mongoClient.getDatabase("ubermongo");
            MongoCollection<Document> collection = database.getCollection("location_events");

            long actionCount = collection.countDocuments(new Document("action", "ANALYTICS_VIEWED"));
            long eventTypeCount = collection.countDocuments(new Document("eventType", "ANALYTICS_VIEWED"));
            long typeCount = collection.countDocuments(new Document("type", "ANALYTICS_VIEWED"));

            return actionCount + eventTypeCount + typeCount;
        }
    }

    // =====================================================
    // Response parsing helpers
    // =====================================================

    private static long longValue(Object value) {
        assertNotNull(value, "Expected numeric value but got null");
        return Long.parseLong(value.toString());
    }

    private static double doubleValue(Object value) {
        assertNotNull(value, "Expected decimal value but got null");
        return Double.parseDouble(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        assertNotNull(value, "Expected map value but got null");
        return (Map<String, Object>) value;
    }

    private static boolean hasHour(Map<String, Object> eventsByHour, String... possibleKeys) {
        for (String key : possibleKeys) {
            if (eventsByHour.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static long getHourCount(Map<String, Object> eventsByHour, String... possibleKeys) {
        for (String key : possibleKeys) {
            if (eventsByHour.containsKey(key)) {
                return longValue(eventsByHour.get(key));
            }
        }

        fail("Missing hour key. Tried: " + String.join(", ", possibleKeys) + ". Actual map: " + eventsByHour);
        return -1;
    }
}