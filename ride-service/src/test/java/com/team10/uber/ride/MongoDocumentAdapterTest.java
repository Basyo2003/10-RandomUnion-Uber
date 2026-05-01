package com.team10.uber.ride;

import com.team10.uber.ride.adapter.MongoDocumentAdapter;
import com.team10.uber.ride.dto.RideEventDTO;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MongoDocumentAdapterTest {

    @Test
    void mongoDocumentAdapter_adapt_populatesFields() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        ObjectId objectId = new ObjectId();
        Document doc = new Document();
        doc.put("_id", objectId);
        doc.put("rideId", 200L);
        doc.put("action", "RIDE_COMPLETED");
        doc.put("timestamp", LocalDateTime.of(2024, 6, 10, 18, 45).toString());

        RideEventDTO result = adapter.adapt(doc);

        assertNotNull(result, "adapt() must return non-null");
        assertEquals(objectId.toHexString(), result.getId(), "adapt() must map _id to id");
        assertEquals(200L, result.getRideId(), "adapt() must map rideId");
        assertEquals("RIDE_COMPLETED", result.getAction(), "adapt() must map action");
        assertNotNull(result.getTimestamp(), "adapt() must map timestamp");
    }

    @Test
    void mongoDocumentAdapter_adapt_handlesNullId() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        Document doc = new Document();
        doc.put("rideId", 15L);
        doc.put("action", "RIDE_CANCELLED");

        RideEventDTO result = adapter.adapt(doc);
        assertNotNull(result, "adapt() must return non-null even with null _id");
        assertNull(result.getId(), "adapt() must return null id when _id is missing");
        assertEquals("RIDE_CANCELLED", result.getAction());
    }
}
