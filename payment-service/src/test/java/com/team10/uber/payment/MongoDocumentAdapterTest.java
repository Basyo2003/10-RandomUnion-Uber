package com.team10.uber.payment;

import com.team10.uber.payment.adapter.MongoDocumentAdapter;
import com.team10.uber.payment.dto.PaymentAuditEventDTO;
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
        doc.put("paymentId", 500L);
        doc.put("action", "PAYMENT_PROCESSED");
        doc.put("timestamp", LocalDateTime.of(2024, 7, 25, 12, 0).toString());
        doc.put("method", "CREDIT_CARD");
        doc.put("amount", 99.99);

        PaymentAuditEventDTO result = adapter.adapt(doc);

        assertNotNull(result, "adapt() must return non-null");
        assertEquals(objectId.toHexString(), result.getId(), "adapt() must map _id to id");
        assertEquals(500L, result.getPaymentId(), "adapt() must map paymentId");
        assertEquals("PAYMENT_PROCESSED", result.getAction(), "adapt() must map action");
        assertNotNull(result.getTimestamp(), "adapt() must map timestamp");
        assertEquals("CREDIT_CARD", result.getMethod(), "adapt() must map method");
        assertEquals(99.99, result.getAmount(), 0.001, "adapt() must map amount");
    }

    @Test
    void mongoDocumentAdapter_adapt_handlesNullId() {
        MongoDocumentAdapter adapter = new MongoDocumentAdapter();

        Document doc = new Document();
        doc.put("paymentId", 1L);
        doc.put("action", "PAYMENT_REFUNDED");
        doc.put("method", "PAYPAL");

        PaymentAuditEventDTO result = adapter.adapt(doc);
        assertNotNull(result, "adapt() must return non-null even with null _id");
        assertNull(result.getId(), "adapt() must return null id when _id is missing");
        assertEquals("PAYMENT_REFUNDED", result.getAction());
    }
}
