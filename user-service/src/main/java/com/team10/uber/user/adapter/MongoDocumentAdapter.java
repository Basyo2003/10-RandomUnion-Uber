package com.team10.uber.user.adapter;

import com.team10.uber.user.dto.AuthEventDTO;
import org.bson.Document;

public class MongoDocumentAdapter implements NoSqlAdapter<Document, AuthEventDTO> {
    @Override
    public AuthEventDTO adapt(Document source) {
        AuthEventDTO dto = new AuthEventDTO();
        dto.setId(source.getObjectId("_id") != null ? source.getObjectId("_id").toHexString() : null);
        dto.setUserId(source.containsKey("userId") ? ((Number) source.get("userId")).longValue() : null);
        dto.setAction(source.getString("action"));
        String ts = source.getString("timestamp");
        dto.setTimestamp(ts != null ? java.time.LocalDateTime.parse(ts) : null);
        return dto;
    }
}
