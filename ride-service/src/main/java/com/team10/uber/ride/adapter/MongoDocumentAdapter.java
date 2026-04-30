package com.team10.uber.ride.adapter;

import com.team10.uber.ride.dto.RideEventDTO;
import org.bson.Document;

public class MongoDocumentAdapter implements NoSqlAdapter<Document, RideEventDTO> {
    @Override
    public RideEventDTO adapt(Document source) {
        RideEventDTO dto = new RideEventDTO();
        dto.setId(source.getObjectId("_id") != null ? source.getObjectId("_id").toHexString() : null);
        dto.setRideId(source.containsKey("rideId") ? ((Number) source.get("rideId")).longValue() : null);
        dto.setAction(source.getString("action"));
        String ts = source.getString("timestamp");
        dto.setTimestamp(ts != null ? java.time.LocalDateTime.parse(ts) : null);
        return dto;
    }
}
