package com.team10.uber.location;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S4F11TrackingTests {

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
    void clearMongoBeforeEach() {
        clearMongoLocationEvents();
    }

    // =====================================================
    // TC118
    // Tracking event writes Cassandra row + Mongo TRACKING_RECORDED
    // =====================================================
    @Test
    @Order(118)
    void TC118_trackingEventWritesCassandraRowAndMongoTrackingRecorded() throws Exception {
        long driverId = seedDriver("TC118_D1");

        long mongoBefore = countTrackingRecordedDocs();

        String body = """
                {
                  "latitude": 30.0444,
                  "longitude": 31.2357,
                  "speed": 45.0,
                  "heading": 180.0,
                  "accuracy": 5.0,
                  "notes": "TC118 tracking event"
                }
                """;

        HttpResponse<String> response = postTrackingWithAuth(driverId, body);

        assertTrue(is2xx(response.statusCode()), response.body());

        waitForCassandraWrite(driverId, 1);

        long cassandraCount = cassandraCount(driverId);
        long mongoAfter = countTrackingRecordedDocs();

        assertTrue(cassandraCount >= 1, "TC118 failed: Cassandra row count should be >= 1");
        assertTrue(mongoAfter > mongoBefore, "TC118 failed: TRACKING_RECORDED Mongo count should increase");
    }

    // =====================================================
    // TC119
    // Cassandra tracking row has latitude/longitude/speed populated
    // =====================================================
    @Test
    @Order(119)
    void TC119_cassandraTrackingRowHasLatitudeLongitudeSpeedPopulated() throws Exception {
        long driverId = seedDriver("TC119_D1");

        String body = """
                {
                  "latitude": 30.0444,
                  "longitude": 31.2357,
                  "speed": 45.0,
                  "heading": 180.0,
                  "accuracy": 5.0
                }
                """;

        HttpResponse<String> response = postTrackingWithAuth(driverId, body);

        assertTrue(is2xx(response.statusCode()), response.body());

        waitForCassandraWrite(driverId, 1);

        List<Row> rows = cassandraRows(driverId);

        assertFalse(rows.isEmpty(), "TC119 failed: Cassandra row should exist");

        Row row = rows.get(0);

        assertEquals(30.0444, row.getDouble("latitude"), 0.01);
        assertEquals(31.2357, row.getDouble("longitude"), 0.01);
        assertEquals(45.0, row.getDouble("speed"), 0.01);
    }

    // =====================================================
    // TC120
    // Two tracking events -> both rows present in Cassandra
    // =====================================================
    @Test
    @Order(120)
    void TC120_twoTrackingEventsBothRowsPresentInCassandra() throws Exception {
        long driverId = seedDriver("TC120_D1");

        String body1 = """
                {
                  "latitude": 30.04,
                  "longitude": 31.23,
                  "speed": 40.0,
                  "notes": "TC120 first"
                }
                """;

        String body2 = """
                {
                  "latitude": 30.05,
                  "longitude": 31.24,
                  "speed": 50.0,
                  "notes": "TC120 second"
                }
                """;

        HttpResponse<String> response1 = postTrackingWithAuth(driverId, body1);
        Thread.sleep(50);
        HttpResponse<String> response2 = postTrackingWithAuth(driverId, body2);

        assertTrue(is2xx(response1.statusCode()), response1.body());
        assertTrue(is2xx(response2.statusCode()), response2.body());

        waitForCassandraWrite(driverId, 2);

        assertTrue(
                cassandraCount(driverId) >= 2,
                "TC120 failed: two tracking events should create at least two Cassandra rows"
        );
    }

    // =====================================================
    // TC121
    // Tracking event for non-existent driver returns 404
    // =====================================================
    @Test
    @Order(121)
    void TC121_trackingEventForNonExistentDriverReturns404() throws Exception {
        String body = """
                {
                  "latitude": 30.04,
                  "longitude": 31.23,
                  "speed": 40.0
                }
                """;

        HttpResponse<String> response = postTrackingWithAuth(999999L, body);

        assertEquals(404, response.statusCode(), response.body());
    }

    // =====================================================
    // TC122
    // Tracking without Authorization header returns 401
    // =====================================================
    @Test
    @Order(122)
    void TC122_trackingWithoutAuthorizationReturns401() throws Exception {
        long driverId = seedDriver("TC122_D1");

        String body = """
                {
                  "latitude": 30.04,
                  "longitude": 31.23,
                  "speed": 40.0
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/" + driverId + "/tracking"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(), response.body());
    }

    // =====================================================
    // TC123
    // Tracking with malformed JWT returns 401
    // =====================================================
    @Test
    @Order(123)
    void TC123_trackingWithMalformedJwtReturns401() throws Exception {
        long driverId = seedDriver("TC123_D1");

        String body = """
                {
                  "latitude": 30.04,
                  "longitude": 31.23,
                  "speed": 40.0
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION_SERVICE_URL + "/api/locations/" + driverId + "/tracking"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer xxx.yyy.zzz")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(), response.body());
    }

    // =====================================================
    // TC124
    // Tracking event with only required lat/lon -> 2xx
    // =====================================================
    @Test
    @Order(124)
    void TC124_trackingWithOnlyRequiredLatLonReturns2xx() throws Exception {
        long driverId = seedDriver("TC124_D1");

        String body = """
                {
                  "latitude": 30.04,
                  "longitude": 31.23
                }
                """;

        HttpResponse<String> response = postTrackingWithAuth(driverId, body);

        assertTrue(is2xx(response.statusCode()), response.body());

        waitForCassandraWrite(driverId, 1);

        assertTrue(cassandraCount(driverId) >= 1);
    }

    // =====================================================
    // TC125
    // TRACKING_RECORDED Mongo doc carries driverId in details
    // =====================================================
    @Test
    @Order(125)
    void TC125_trackingRecordedMongoDocCarriesDriverId() throws Exception {
        long driverId = seedDriver("TC125_D1");

        String body = """
                {
                  "latitude": 30.04,
                  "longitude": 31.23,
                  "speed": 40.0,
                  "notes": "TC125 tracking event"
                }
                """;

        HttpResponse<String> response = postTrackingWithAuth(driverId, body);

        assertTrue(is2xx(response.statusCode()), response.body());

        waitForMongoTrackingRecordedWithDriverId(driverId);

        long matchingDocs = countTrackingRecordedDocsForDriver(driverId);

        assertTrue(
                matchingDocs >= 1,
                "TC125 failed: TRACKING_RECORDED Mongo doc should include matching driverId"
        );
    }

    // =====================================================
    // TC126
    // Cassandra tracking row preserves exact coordinates
    // =====================================================
    @Test
    @Order(126)
    void TC126_cassandraTrackingRowPreservesExactCoordinates() throws Exception {
        long driverId = seedDriver("TC126_D1");

        String body = """
                {
                  "latitude": 30.12345,
                  "longitude": 31.67890,
                  "speed": 42.0
                }
                """;

        HttpResponse<String> response = postTrackingWithAuth(driverId, body);

        assertTrue(is2xx(response.statusCode()), response.body());

        waitForCassandraWrite(driverId, 1);

        List<Row> rows = cassandraRows(driverId);

        assertFalse(rows.isEmpty(), "TC126 failed: Cassandra row should exist");

        Row row = rows.get(0);

        assertEquals(30.12345, row.getDouble("latitude"), 0.0001);
        assertEquals(31.67890, row.getDouble("longitude"), 0.0001);
    }

    // =====================================================
    // TC127
    // Notes field round-trips into Cassandra row
    // =====================================================
    @Test
    @Order(127)
    void TC127_notesFieldRoundTripsIntoCassandraRow() throws Exception {
        long driverId = seedDriver("TC127_D1");
        String notes = "TC127_" + UUID.randomUUID();

        String body = """
                {
                  "latitude": 30.04,
                  "longitude": 31.23,
                  "speed": 40.0,
                  "notes": "%s"
                }
                """.formatted(notes);

        HttpResponse<String> response = postTrackingWithAuth(driverId, body);

        assertTrue(is2xx(response.statusCode()), response.body());

        waitForCassandraWrite(driverId, 1);

        List<Row> rows = cassandraRows(driverId);

        assertFalse(rows.isEmpty(), "TC127 failed: Cassandra row should exist");

        boolean foundNotes = rows.stream()
                .anyMatch(row -> notes.equals(row.getString("notes")));

        assertTrue(foundNotes, "TC127 failed: notes should match exactly in Cassandra row");
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
    // PostgreSQL helpers
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
    // Cassandra helpers
    // =====================================================

    private static CqlSession cassandraSession() {
        return CqlSession.builder()
                .addContactPoint(new InetSocketAddress("localhost", 9042))
                .withLocalDatacenter("datacenter1")
                .withKeyspace("uberks")
                .build();
    }

    private static long cassandraCount(long driverId) {
        try (CqlSession session = cassandraSession()) {
            Row row = session.execute(
                    "SELECT COUNT(*) FROM location_tracking_events WHERE driver_id = ?",
                    driverId
            ).one();

            assertNotNull(row);
            return row.getLong(0);
        }
    }

    private static List<Row> cassandraRows(long driverId) {
        try (CqlSession session = cassandraSession()) {
            return session.execute(
                    "SELECT driver_id, timestamp, latitude, longitude, speed, heading, accuracy, ride_id, notes " +
                            "FROM location_tracking_events WHERE driver_id = ?",
                    driverId
            ).all();
        }
    }

    private static void waitForCassandraWrite(long driverId, long expectedMinCount) throws Exception {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(8).toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (cassandraCount(driverId) >= expectedMinCount) {
                return;
            }

            Thread.sleep(250);
        }

        fail("Timed out waiting for Cassandra row count >= " + expectedMinCount + " for driverId=" + driverId);
    }

    // =====================================================
    // Mongo helpers
    // =====================================================

    private static void clearMongoLocationEvents() {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
            MongoDatabase database = mongoClient.getDatabase("ubermongo");
            MongoCollection<Document> collection = database.getCollection("location_events");
            collection.deleteMany(new Document());
        }
    }

    private static long countTrackingRecordedDocs() {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
            MongoCollection<Document> collection =
                    mongoClient.getDatabase("ubermongo").getCollection("location_events");

            long actionCount = collection.countDocuments(new Document("action", "TRACKING_RECORDED"));
            long eventTypeCount = collection.countDocuments(new Document("eventType", "TRACKING_RECORDED"));
            long typeCount = collection.countDocuments(new Document("type", "TRACKING_RECORDED"));

            return actionCount + eventTypeCount + typeCount;
        }
    }

    private static long countTrackingRecordedDocsForDriver(long driverId) {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
            MongoCollection<Document> collection =
                    mongoClient.getDatabase("ubermongo").getCollection("location_events");

            long count = 0;

            count += collection.countDocuments(new Document("action", "TRACKING_RECORDED")
                    .append("details.driverId", driverId));

            count += collection.countDocuments(new Document("eventType", "TRACKING_RECORDED")
                    .append("details.driverId", driverId));

            count += collection.countDocuments(new Document("type", "TRACKING_RECORDED")
                    .append("details.driverId", driverId));

            count += collection.countDocuments(new Document("action", "TRACKING_RECORDED")
                    .append("details.driver_id", driverId));

            count += collection.countDocuments(new Document("eventType", "TRACKING_RECORDED")
                    .append("details.driver_id", driverId));

            count += collection.countDocuments(new Document("type", "TRACKING_RECORDED")
                    .append("details.driver_id", driverId));

            count += collection.countDocuments(new Document("action", "TRACKING_RECORDED")
                    .append("driverId", driverId));

            count += collection.countDocuments(new Document("eventType", "TRACKING_RECORDED")
                    .append("driverId", driverId));

            count += collection.countDocuments(new Document("type", "TRACKING_RECORDED")
                    .append("driverId", driverId));

            return count;
        }
    }

    private static void waitForMongoTrackingRecordedWithDriverId(long driverId) throws Exception {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(8).toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (countTrackingRecordedDocsForDriver(driverId) >= 1) {
                return;
            }

            Thread.sleep(250);
        }

        fail("Timed out waiting for TRACKING_RECORDED Mongo doc with driverId=" + driverId);
    }
}