package com.team10.uber.location.observer;

import com.team10.uber.location.event.LocationEvent;
import com.team10.uber.location.event.MongoEvent;
import com.team10.uber.location.factory.EventFactory;
import com.team10.uber.location.factory.EventType;
import com.team10.uber.location.model.Location;
import com.team10.uber.location.repository.mongo.LocationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);
    private static final EventType BOUND_TYPE = EventType.LOCATION;

    private final LocationEventRepository repository;
    private final EventFactory eventFactory = new EventFactory();

    public MongoEventLogger(LocationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = buildParams(eventType, payload);
            MongoEvent event = eventFactory.createEvent(BOUND_TYPE, params);
            repository.save((LocationEvent) event);
        } catch (Exception ex) {
            // Mongo failure must NEVER roll back the upstream PG transaction
            log.warn("[MongoEventLogger][location] Failed to persist event action={} error={}",
                    eventType, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildParams(String eventType, Object payload) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", eventType);
        params.put("timestamp", LocalDateTime.now().toString());
        if (payload instanceof Location l) {
            params.put("driverId", l.getDriverId());
            params.put("latitude", l.getLatitude());
            params.put("longitude", l.getLongitude());
        } else if (payload instanceof Long id) {
            params.put("driverId", id);
        } else if (payload instanceof Map<?, ?> map) {
            params.putAll((Map<String, Object>) map);
        }
        return params;
    }
}
