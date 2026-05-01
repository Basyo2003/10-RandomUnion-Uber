package com.team10.uber.driver;

import com.team10.uber.driver.adapter.MongoDocumentAdapter;
import com.team10.uber.driver.dto.DriverEventDTO;
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
        doc.put("driverId", 99L);
        doc.put("action", "DRIVER_CREATED");
        doc.put("timestamp", LocalDateTime.of(2024, 3, 20, 14, 0).toString());

        DriverEventDTO result = adapter.adapt(doc);

        assertNotNull(result, "adapt() must return non-null");
        assertEquals(objectId.toHexString(), result.getId(), "adapt() must map _id to id");
        assertEquals(99L, result.getDriverId(), "adapt() must map driverId");
        assertEquals("DRIVER_CREATED", result.getAction(), "adapt() must map action");
        assertNotNull(result.getTimestamp(), "adapt() must map timestamp");
    }

    @Test
    void mongoDocumentAdapter_adapt_handlesNullId() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        Document doc = new Document();
        doc.put("driverId", 5L);
        doc.put("action", "DRIVER_UPDATED");

        DriverEventDTO result = adapter.adapt(doc);
        assertNotNull(result, "adapt() must return non-null even with null _id");
        assertNull(result.getId(), "adapt() must return null id when _id is missing");
        assertEquals("DRIVER_UPDATED", result.getAction());
    }
}
