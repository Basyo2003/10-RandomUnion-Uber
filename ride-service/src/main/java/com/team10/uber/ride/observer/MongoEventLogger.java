package com.team10.uber.ride.observer;

import com.team10.uber.ride.event.MongoEvent;
import com.team10.uber.ride.event.RideEvent;
import com.team10.uber.ride.factory.EventFactory;
import com.team10.uber.ride.factory.EventType;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.repository.mongo.RideEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);
    private static final EventType BOUND_TYPE = EventType.RIDE;

    private final RideEventRepository repository;
    private final EventFactory eventFactory = new EventFactory();

    public MongoEventLogger(RideEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = buildParams(eventType, payload);
            MongoEvent event = eventFactory.createEvent(BOUND_TYPE, params);
            repository.save((RideEvent) event);
        } catch (Exception ex) {
            // Mongo failure must NEVER roll back the upstream PG transaction
            log.warn("[MongoEventLogger][ride] Failed to persist event action={} error={}",
                    eventType, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildParams(String eventType, Object payload) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", eventType);
        params.put("timestamp", LocalDateTime.now().toString());
        if (payload instanceof Ride r) {
            params.put("rideId", r.getId());
            params.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            params.put("userId", r.getUserId());
            params.put("driverId", r.getDriverId());
        } else if (payload instanceof Long id) {
            params.put("rideId", id);
        } else if (payload instanceof Map<?, ?> map) {
            params.putAll((Map<String, Object>) map);
        }
        return params;
    }
}
