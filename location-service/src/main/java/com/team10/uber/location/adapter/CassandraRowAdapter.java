package com.team10.uber.location.adapter;

import com.team10.uber.location.cassandra.LocationTrackingEvent;
import com.team10.uber.location.dto.LocationTrackingEventDTO;
import org.springframework.stereotype.Component;

@Component
public class CassandraRowAdapter {

    public LocationTrackingEventDTO adapt(LocationTrackingEvent event) {
        if (event == null || event.getKey() == null) {
            return null;
        }

        return new LocationTrackingEventDTO(
                event.getKey().getDriverId(),
                event.getKey().getTimestamp(),
                event.getLatitude(),
                event.getLongitude(),
                event.getSpeed(),
                event.getHeading(),
                event.getAccuracy(),
                event.getRideId(),
                event.getNotes()
        );
    }
}