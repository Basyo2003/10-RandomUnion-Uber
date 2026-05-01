package com.team10.uber.location;

import com.team10.uber.location.event.*;
import com.team10.uber.location.factory.EventFactory;
import com.team10.uber.location.factory.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventFactoryUnitTest {

    private EventFactory factory;

    @BeforeEach
    void setUp() {
        factory = new EventFactory();
    }

    @Test
    void createEvent_AUTH_returnsAuthEvent() {
        Map<String, Object> params = Map.of(
                "userId", 1L,
                "action", "LOGIN"
        );
        MongoEvent event = factory.createEvent(EventType.AUTH, params);
        assertNotNull(event, "createEvent(AUTH, ...) must return non-null");
        assertInstanceOf(AuthEvent.class, event, "createEvent(AUTH, ...) must return AuthEvent");
        assertEquals("LOGIN", event.getAction());
    }

    @Test
    void createEvent_DRIVER_returnsDriverEvent() {
        Map<String, Object> params = Map.of(
                "driverId", 10L,
                "action", "DRIVER_CREATED"
        );
        MongoEvent event = factory.createEvent(EventType.DRIVER, params);
        assertNotNull(event);
        assertInstanceOf(DriverEvent.class, event, "createEvent(DRIVER, ...) must return DriverEvent");
        assertEquals("DRIVER_CREATED", event.getAction());
    }

    @Test
    void createEvent_RIDE_returnsRideEvent() {
        Map<String, Object> params = Map.of(
                "rideId", 20L,
                "action", "RIDE_STARTED"
        );
        MongoEvent event = factory.createEvent(EventType.RIDE, params);
        assertNotNull(event);
        assertInstanceOf(RideEvent.class, event, "createEvent(RIDE, ...) must return RideEvent");
        assertEquals("RIDE_STARTED", event.getAction());
    }

    @Test
    void createEvent_LOCATION_returnsLocationEvent() {
        Map<String, Object> params = Map.of(
                "driverId", 5L,
                "action", "LOCATION_UPDATED"
        );
        MongoEvent event = factory.createEvent(EventType.LOCATION, params);
        assertNotNull(event);
        assertInstanceOf(LocationEvent.class, event, "createEvent(LOCATION, ...) must return LocationEvent");
    }

    @Test
    void createEvent_PAYMENT_AUDIT_exposesMethodAndAmount() {
        Map<String, Object> params = Map.of(
                "paymentId", 100L,
                "action", "PAYMENT_PROCESSED",
                "method", "CREDIT_CARD",
                "amount", 50.0
        );
        MongoEvent event = factory.createEvent(EventType.PAYMENT_AUDIT, params);
        assertNotNull(event);
        assertInstanceOf(PaymentAuditEvent.class, event,
                "createEvent(PAYMENT_AUDIT, ...) must return PaymentAuditEvent");
        PaymentAuditEvent auditEvent = (PaymentAuditEvent) event;
        assertEquals("CREDIT_CARD", auditEvent.getMethod());
        assertEquals(50.0, auditEvent.getAmount(), 0.001);
    }
}
