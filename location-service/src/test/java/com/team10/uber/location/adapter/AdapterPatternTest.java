package com.team10.uber.location.adapter;

import com.team10.uber.location.cassandra.LocationTrackingEvent;
import com.team10.uber.location.cassandra.LocationTrackingEventKey;
import com.team10.uber.location.dto.LocationEventDTO;
import com.team10.uber.location.dto.LocationTrackingEventDTO;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter pattern verification — location-service.
 * Covers spec scenarios: (a) class existence, (b) adapt() return type,
 * Mongo adapter field mapping, Cassandra adapter field mapping.
 */
class AdapterPatternTest {

    // ── (a) Adapter class existence ──────────────────────────────────────────

    @Test
    void mongoDocumentAdapterClassExists() {
        assertDoesNotThrow(
                () -> Class.forName("com.team10.uber.location.adapter.MongoDocumentAdapter"),
                "MongoDocumentAdapter must exist in location-service");
    }

    @Test
    void cassandraRowAdapterClassExists() {
        assertDoesNotThrow(
                () -> Class.forName("com.team10.uber.location.adapter.CassandraRowAdapter"),
                "CassandraRowAdapter must exist in location-service");
    }

    // ── (b) adapt() return types ──────────────────────────────────────────────

    @Test
    void mongoDocumentAdapterAdaptReturnTypeIsLocationEventDTO() throws Exception {
        Method adapt = MongoDocumentAdapter.class.getMethod("adapt", Document.class);
        assertEquals(LocationEventDTO.class, adapt.getReturnType(),
                "MongoDocumentAdapter.adapt() must return LocationEventDTO");
    }

    @Test
    void cassandraRowAdapterAdaptReturnTypeIsLocationTrackingEventDTO() throws Exception {
        Method adapt = CassandraRowAdapter.class.getMethod("adapt", LocationTrackingEvent.class);
        assertEquals(LocationTrackingEventDTO.class, adapt.getReturnType(),
                "CassandraRowAdapter.adapt() must return LocationTrackingEventDTO");
    }

    @Test
    void adaptMethodsArePublicAndNonStatic() throws Exception {
        Method mongoAdapt = MongoDocumentAdapter.class.getMethod("adapt", Document.class);
        assertTrue(Modifier.isPublic(mongoAdapt.getModifiers()));
        assertFalse(Modifier.isStatic(mongoAdapt.getModifiers()));

        Method cassandraAdapt = CassandraRowAdapter.class.getMethod("adapt", LocationTrackingEvent.class);
        assertTrue(Modifier.isPublic(cassandraAdapt.getModifiers()));
        assertFalse(Modifier.isStatic(cassandraAdapt.getModifiers()));
    }

    // ── Mongo adapter — functional ────────────────────────────────────────────

    @Test
    void mongoDocumentAdapterPopulatesLocationEventFields() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        ObjectId oid = new ObjectId();
        Document doc = new Document();
        doc.put("_id", oid);
        doc.put("driverId", 11L);
        doc.put("action", "TRACKING_RECORDED");
        doc.put("timestamp", new Date()); // BSON Date
        doc.put("details", Map.of("latitude", 30.04, "longitude", 31.24));

        LocationEventDTO dto = adapter.adapt(doc);

        assertEquals(oid.toHexString(), dto.getId());
        assertEquals(11L, dto.getDriverId());
        assertEquals("TRACKING_RECORDED", dto.getAction());
        assertNotNull(dto.getTimestamp());
        assertNotNull(dto.getDetails());
    }

    @Test
    void mongoDocumentAdapterHandlesNullTimestamp() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        Document doc = new Document();
        doc.put("driverId", 1L);
        doc.put("action", "ANALYTICS_VIEWED");

        LocationEventDTO dto = adapter.adapt(doc);

        assertNotNull(dto);
        assertNull(dto.getTimestamp());
    }

    @Test
    void mongoDocumentAdapterHandlesMissingFieldsGracefully() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();
        Document doc = new Document();

        assertDoesNotThrow(() -> adapter.adapt(doc),
                "adapt() must not throw when optional fields are absent");
    }

    // ── Cassandra adapter — functional ────────────────────────────────────────

    @Test
    void cassandraAdapterMapsAllFields() {
        CassandraRowAdapter adapter = new CassandraRowAdapter();

        Instant ts = Instant.parse("2026-04-20T12:00:00Z");
        LocationTrackingEventKey key = new LocationTrackingEventKey(99L, ts);
        LocationTrackingEvent event = new LocationTrackingEvent(
                key,
                30.0444,  // latitude
                31.2357,  // longitude
                60.0,     // speed
                180.0,    // heading
                5.0,      // accuracy
                42L,      // rideId
                "en route to pickup"
        );

        LocationTrackingEventDTO dto = adapter.adapt(event);

        assertNotNull(dto);
        assertEquals(99L, dto.getDriverId());
        assertEquals(ts, dto.getTimestamp());
        assertEquals(30.0444, dto.getLatitude());
        assertEquals(31.2357, dto.getLongitude());
        assertEquals(60.0, dto.getSpeed());
        assertEquals(180.0, dto.getHeading());
        assertEquals(5.0, dto.getAccuracy());
        assertEquals(42L, dto.getRideId());
        assertEquals("en route to pickup", dto.getNotes());
    }

    @Test
    void cassandraAdapterReturnsNullForNullEvent() {
        CassandraRowAdapter adapter = new CassandraRowAdapter();

        LocationTrackingEventDTO dto = adapter.adapt(null);

        assertNull(dto, "adapt(null) must return null");
    }

    @Test
    void cassandraAdapterReturnsNullWhenKeyIsNull() {
        CassandraRowAdapter adapter = new CassandraRowAdapter();

        LocationTrackingEvent event = new LocationTrackingEvent(
                null,   // key is null
                30.0, 31.0, 45.0, 90.0, 3.0, null, null
        );

        LocationTrackingEventDTO dto = adapter.adapt(event);

        assertNull(dto, "adapt() must return null when event key is null");
    }

    @Test
    void cassandraAdapterHandlesNullOptionalFields() {
        CassandraRowAdapter adapter = new CassandraRowAdapter();

        Instant ts = Instant.now();
        LocationTrackingEventKey key = new LocationTrackingEventKey(55L, ts);
        LocationTrackingEvent event = new LocationTrackingEvent(
                key,
                30.0, 31.0,
                null,   // speed optional
                null,   // heading optional
                null,   // accuracy optional
                null,   // rideId optional
                null    // notes optional
        );

        LocationTrackingEventDTO dto = adapter.adapt(event);

        assertNotNull(dto);
        assertEquals(55L, dto.getDriverId());
        assertNull(dto.getSpeed());
        assertNull(dto.getRideId());
        assertNull(dto.getNotes());
    }
}
