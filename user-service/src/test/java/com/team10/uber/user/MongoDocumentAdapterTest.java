package com.team10.uber.user;

import com.team10.uber.user.adapter.MongoDocumentAdapter;
import com.team10.uber.user.dto.AuthEventDTO;
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
        doc.put("userId", 42L);
        doc.put("action", "LOGIN");
        doc.put("timestamp", LocalDateTime.of(2024, 1, 15, 10, 30).toString());

        AuthEventDTO result = adapter.adapt(doc);

        assertNotNull(result, "adapt() must return non-null");
        assertEquals(objectId.toHexString(), result.getId(), "adapt() must map _id to id");
        assertEquals(42L, result.getUserId(), "adapt() must map userId");
        assertEquals("LOGIN", result.getAction(), "adapt() must map action");
        assertNotNull(result.getTimestamp(), "adapt() must map timestamp");
    }

    @Test
    void mongoDocumentAdapter_adapt_handlesNullId() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        Document doc = new Document();
        doc.put("userId", 10L);
        doc.put("action", "LOGOUT");

        AuthEventDTO result = adapter.adapt(doc);
        assertNotNull(result, "adapt() must return non-null even with null _id");
        assertNull(result.getId(), "adapt() must return null id when _id is missing");
        assertEquals("LOGOUT", result.getAction());
    }
}
