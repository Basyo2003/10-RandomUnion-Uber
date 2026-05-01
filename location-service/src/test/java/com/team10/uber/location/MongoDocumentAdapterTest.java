package com.team10.uber.location;

import com.team10.uber.location.adapter.MongoDocumentAdapter;
import com.team10.uber.location.dto.LocationEventDTO;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class MongoDocumentAdapterTest {

    @Test
    void mongoDocumentAdapter_adapt_populatesFields() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        ObjectId objectId = new ObjectId();
        Document doc = new Document();
        doc.put("_id", objectId);
        doc.put("driverId", 77L);
        doc.put("action", "LOCATION_UPDATED");
        // location adapter reads timestamp as Date, not String
        doc.put("timestamp", new Date());

        LocationEventDTO result = adapter.adapt(doc);

        assertNotNull(result, "adapt() must return non-null");
        assertEquals(objectId.toHexString(), result.getId(), "adapt() must map _id to id");
        assertEquals(77L, result.getDriverId(), "adapt() must map driverId");
        assertEquals("LOCATION_UPDATED", result.getAction(), "adapt() must map action");
        assertNotNull(result.getTimestamp(), "adapt() must map timestamp");
    }

    @Test
    void mongoDocumentAdapter_adapt_handlesNullId() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        Document doc = new Document();
        doc.put("driverId", 3L);
        doc.put("action", "DRIVER_IDLE");

        LocationEventDTO result = adapter.adapt(doc);
        assertNotNull(result, "adapt() must return non-null even with null _id");
        assertNull(result.getId(), "adapt() must return null id when _id is missing");
        assertEquals("DRIVER_IDLE", result.getAction());
    }
}
