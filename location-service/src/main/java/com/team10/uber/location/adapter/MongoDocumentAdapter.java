package com.team10.uber.location.adapter;

import com.team10.uber.location.dto.LocationEventDTO;
import org.bson.Document;
import org.springframework.stereotype.Component;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class MongoDocumentAdapter {

    public LocationEventDTO adapt(Document document) {
        LocationEventDTO dto = new LocationEventDTO();

        if (document.getObjectId("_id") != null) {
            dto.setId(document.getObjectId("_id").toHexString());
        }

        dto.setDriverId(document.getLong("driverId"));
        dto.setAction(document.getString("action"));

        Date timestamp = document.getDate("timestamp");
        if (timestamp != null) {
            dto.setTimestamp(
                    timestamp.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
            );
        }

        Object detailsObject = document.get("details");
        if (detailsObject instanceof Map<?, ?> detailsMap) {
            Map<String, Object> details = new HashMap<>();

            for (Map.Entry<?, ?> entry : detailsMap.entrySet()) {
                if (entry.getKey() != null) {
                    details.put(entry.getKey().toString(), entry.getValue());
                }
            }

            dto.setDetails(details);
        }

        return dto;
    }
}