package com.team10.uber.payment.adapter;

import com.team10.uber.payment.dto.PaymentAuditEventDTO;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

public class MongoDocumentAdapter implements NoSqlAdapter<Document, PaymentAuditEventDTO> {
    @Override
    public PaymentAuditEventDTO adapt(Document source) {
        PaymentAuditEventDTO dto = new PaymentAuditEventDTO();
        dto.setId(source.getObjectId("_id") != null ? source.getObjectId("_id").toHexString() : null);
        dto.setPaymentId(source.containsKey("paymentId") && source.get("paymentId") != null
                ? ((Number) source.get("paymentId")).longValue() : null);
        dto.setAction(source.getString("action"));
        dto.setTimestamp(parseTimestamp(source.get("timestamp")));
        dto.setMethod(source.getString("method"));
        dto.setAmount(source.containsKey("amount") && source.get("amount") != null
                ? ((Number) source.get("amount")).doubleValue() : null);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) source.get("details");
        dto.setDetails(details);
        return dto;
    }

    private LocalDateTime parseTimestamp(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Date) {
            return ((Date) raw).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (raw instanceof String) {
            return LocalDateTime.parse((String) raw);
        }
        return null;
    }
}
