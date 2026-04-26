package com.team10.uber.user.observer;

import com.team10.uber.user.event.AuthEvent;
import com.team10.uber.user.event.MongoEvent;
import com.team10.uber.user.factory.EventFactory;
import com.team10.uber.user.factory.EventType;
import com.team10.uber.user.model.User;
import com.team10.uber.user.repository.mongo.AuthEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);
    private static final EventType BOUND_TYPE = EventType.AUTH;

    private final AuthEventRepository repository;
    private final EventFactory eventFactory = new EventFactory();

    public MongoEventLogger(AuthEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = buildParams(eventType, payload);
            MongoEvent event = eventFactory.createEvent(BOUND_TYPE, params);
            repository.save((AuthEvent) event);
        } catch (Exception ex) {
            // Mongo failure must NEVER roll back the upstream PG transaction
            log.warn("[MongoEventLogger][user] Failed to persist event action={} error={}",
                    eventType, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildParams(String eventType, Object payload) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", eventType);
        params.put("timestamp", LocalDateTime.now().toString());
        if (payload instanceof User u) {
            params.put("userId", u.getId());
            params.put("email", u.getEmail());
            params.put("role", u.getRole() != null ? u.getRole().name() : null);
        } else if (payload instanceof Long id) {
            params.put("userId", id);
        } else if (payload instanceof Map<?, ?> map) {
            params.putAll((Map<String, Object>) map);
        }
        return params;
    }
}
