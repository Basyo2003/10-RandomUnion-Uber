package com.team10.uber.payment.adapter;

import com.team10.uber.payment.dto.PaymentAuditEventDTO;
import org.bson.Document;

public class MongoDocumentAdapter implements NoSqlAdapter<Document, PaymentAuditEventDTO> {
    @Override
    public PaymentAuditEventDTO adapt(Document source) {
        PaymentAuditEventDTO dto = new PaymentAuditEventDTO();
        dto.setId(source.getObjectId("_id") != null ? source.getObjectId("_id").toHexString() : null);
        dto.setPaymentId(source.containsKey("paymentId") ? ((Number) source.get("paymentId")).longValue() : null);
        dto.setAction(source.getString("action"));
        String ts = source.getString("timestamp");
        dto.setTimestamp(ts != null ? java.time.LocalDateTime.parse(ts) : null);
        dto.setMethod(source.getString("method"));
        dto.setAmount(source.containsKey("amount") && source.get("amount") != null
            ? ((Number) source.get("amount")).doubleValue() : null);
        return dto;
    }
}
