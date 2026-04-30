package com.team10.uber.driver.adapter;

import com.team10.uber.driver.dto.DriverEventDTO;
import org.bson.Document;

public class MongoDocumentAdapter implements NoSqlAdapter<Document, DriverEventDTO> {
    @Override
    public DriverEventDTO adapt(Document source) {
        DriverEventDTO dto = new DriverEventDTO();
        dto.setId(source.getObjectId("_id") != null ? source.getObjectId("_id").toHexString() : null);
        dto.setDriverId(source.containsKey("driverId") ? ((Number) source.get("driverId")).longValue() : null);
        dto.setAction(source.getString("action"));
        String ts = source.getString("timestamp");
        dto.setTimestamp(ts != null ? java.time.LocalDateTime.parse(ts) : null);
        return dto;
    }
}
