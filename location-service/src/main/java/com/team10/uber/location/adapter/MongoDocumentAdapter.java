package com.team10.uber.location.adapter;

import com.team10.uber.location.dto.LocationEventDTO;
import org.bson.Document;

public class MongoDocumentAdapter implements NoSqlAdapter<Document, LocationEventDTO> {
    @Override
    public LocationEventDTO adapt(Document source) {
        LocationEventDTO dto = new LocationEventDTO();
        dto.setId(source.getObjectId("_id") != null ? source.getObjectId("_id").toHexString() : null);
        dto.setDriverId(source.containsKey("driverId") ? ((Number) source.get("driverId")).longValue() : null);
        dto.setAction(source.getString("action"));
        String ts = source.getString("timestamp");
        dto.setTimestamp(ts != null ? java.time.LocalDateTime.parse(ts) : null);
        return dto;
    }
}
