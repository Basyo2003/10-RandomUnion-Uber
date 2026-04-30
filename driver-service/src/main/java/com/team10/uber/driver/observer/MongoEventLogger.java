package com.team10.uber.driver.observer;

import com.team10.uber.driver.event.DriverEvent;
import com.team10.uber.driver.event.MongoEvent;
import com.team10.uber.driver.factory.EventFactory;
import com.team10.uber.driver.factory.EventType;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.repository.mongo.DriverEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);
    private static final EventType BOUND_TYPE = EventType.DRIVER;

    private final DriverEventRepository repository;
    private final EventFactory eventFactory = new EventFactory();

    public MongoEventLogger(DriverEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = buildParams(eventType, payload);
            MongoEvent event = eventFactory.createEvent(BOUND_TYPE, params);
            repository.save((DriverEvent) event);
        } catch (Exception ex) {
            // Mongo failure must NEVER roll back the upstream PG transaction
            log.warn("[MongoEventLogger][driver] Failed to persist event action={} error={}",
                    eventType, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildParams(String eventType, Object payload) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", eventType);
        params.put("timestamp", LocalDateTime.now().toString());
        if (payload instanceof Driver d) {
            params.put("driverId", d.getId());
            params.put("driverName", d.getName());
            params.put("status", d.getStatus() != null ? d.getStatus().name() : null);
        } else if (payload instanceof Long id) {
            params.put("driverId", id);
        } else if (payload instanceof Map<?, ?> map) {
            params.putAll((Map<String, Object>) map);
        }
        return params;
    }
}
