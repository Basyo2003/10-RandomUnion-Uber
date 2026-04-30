package com.team10.uber.location.service;

import com.team10.uber.location.adapter.CassandraRowAdapter;
import com.team10.uber.location.cassandra.LocationTrackingEvent;
import com.team10.uber.location.cassandra.LocationTrackingEventKey;
import com.team10.uber.location.cassandra.LocationTrackingEventRepository;
import com.team10.uber.location.dto.LocationTrackingEventDTO;
import com.team10.uber.location.model.Location;
import org.springframework.stereotype.Service;
import com.team10.uber.location.dto.TrackingEventRequest;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class LocationTrackingEventService {

    private final LocationTrackingEventRepository locationTrackingEventRepository;
    private final CassandraRowAdapter cassandraRowAdapter;

    public LocationTrackingEventService(
            LocationTrackingEventRepository locationTrackingEventRepository,
            CassandraRowAdapter cassandraRowAdapter
    ) {
        this.locationTrackingEventRepository = locationTrackingEventRepository;
        this.cassandraRowAdapter = cassandraRowAdapter;
    }

    public LocationTrackingEvent recordTrackingEvent(Location location) {
        return recordTrackingEvent(location, null, null);
    }

    public LocationTrackingEvent recordTrackingEvent(
            Location location,
            Long rideId,
            String notes
    ) {
        Instant timestamp = location.getTimestamp() != null
                ? location.getTimestamp()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                : Instant.now();

        LocationTrackingEventKey key = new LocationTrackingEventKey(
                location.getDriverId(),
                timestamp
        );

        Map<String, Object> metadata = location.getMetadata();

        LocationTrackingEvent event = new LocationTrackingEvent(
                key,
                location.getLatitude(),
                location.getLongitude(),
                extractDouble(metadata, "speed"),
                extractDouble(metadata, "heading"),
                extractDouble(metadata, "accuracy"),
                rideId,
                notes
        );

        return locationTrackingEventRepository.save(event);
    }

    public List<LocationTrackingEventDTO> getTrackingEventsForDriver(Long driverId) {
        return locationTrackingEventRepository.findByKeyDriverId(driverId)
                .stream()
                .map(cassandraRowAdapter::adapt)
                .toList();
    }

    private Double extractDouble(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }

        Object value = metadata.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // S4-F11 - record GPS tracking payload directly into Cassandra
    public LocationTrackingEvent recordTrackingEvent(Long driverId, TrackingEventRequest request) {
        LocationTrackingEventKey key = new LocationTrackingEventKey(
                driverId,
                Instant.now()
        );

        LocationTrackingEvent event = new LocationTrackingEvent(
                key,
                request.getLatitude(),
                request.getLongitude(),
                request.getSpeed(),
                request.getHeading(),
                request.getAccuracy(),
                request.getRideId(),
                request.getNotes()
        );

        return locationTrackingEventRepository.save(event);
    }
}