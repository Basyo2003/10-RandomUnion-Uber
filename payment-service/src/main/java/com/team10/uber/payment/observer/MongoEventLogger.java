package com.team10.uber.payment.observer;

import com.team10.uber.payment.event.MongoEvent;
import com.team10.uber.payment.event.PaymentAuditEvent;
import com.team10.uber.payment.factory.EventFactory;
import com.team10.uber.payment.factory.EventType;
import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.repository.mongo.PaymentAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);
    private static final EventType BOUND_TYPE = EventType.PAYMENT_AUDIT;

    private final PaymentAuditEventRepository repository;
    private final EventFactory eventFactory = new EventFactory();

    public MongoEventLogger(PaymentAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = buildParams(eventType, payload);
            MongoEvent event = eventFactory.createEvent(BOUND_TYPE, params);
            repository.save((PaymentAuditEvent) event);
        } catch (Exception ex) {
            // Mongo failure must NEVER roll back the upstream PG transaction
            log.warn("[MongoEventLogger][payment] Failed to persist event action={} error={}",
                    eventType, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildParams(String eventType, Object payload) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", eventType);
        params.put("timestamp", LocalDateTime.now().toString());
        if (payload instanceof Payment p) {
            params.put("paymentId", p.getId());
            params.put("method", p.getMethod() != null ? p.getMethod().name() : null);
            params.put("amount", p.getAmount());
            params.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        } else if (payload instanceof Long id) {
            params.put("paymentId", id);
        } else if (payload instanceof Map<?, ?> map) {
            params.putAll((Map<String, Object>) map);
        }
        return params;
    }
}
