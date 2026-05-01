package com.team10.uber.driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FactoryPatternReflectionTest {

    private static final String PKG = "com.team10.uber.driver";

    @Test
    void mongoEvent_isInterface_withRequiredMethods() throws Exception {
        Class<?> mongoEvent = Class.forName(PKG + ".event.MongoEvent");
        assertTrue(mongoEvent.isInterface(), "MongoEvent must be an interface");

        assertNotNull(mongoEvent.getMethod("getId"));
        assertNotNull(mongoEvent.getMethod("getTimestamp"));
        assertNotNull(mongoEvent.getMethod("getAction"));
        assertNotNull(mongoEvent.getMethod("getDetails"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.team10.uber.driver.event.AuthEvent",
            "com.team10.uber.driver.event.DriverEvent",
            "com.team10.uber.driver.event.RideEvent",
            "com.team10.uber.driver.event.LocationEvent",
            "com.team10.uber.driver.event.PaymentAuditEvent"
    })
    void allEventClasses_implementMongoEvent(String className) throws Exception {
        Class<?> mongoEvent = Class.forName(PKG + ".event.MongoEvent");
        Class<?> eventClass = Class.forName(className);
        assertTrue(mongoEvent.isAssignableFrom(eventClass),
                className + " must implement MongoEvent");
    }

    @Test
    void eventFactory_hasCreateEventMethod() throws Exception {
        Class<?> factoryClass = Class.forName(PKG + ".factory.EventFactory");
        Class<?> eventTypeClass = Class.forName(PKG + ".factory.EventType");
        Method createEvent = factoryClass.getMethod("createEvent", eventTypeClass, Map.class);
        assertNotNull(createEvent, "EventFactory must have createEvent(EventType, Map) method");
        assertFalse(Modifier.isStatic(createEvent.getModifiers()),
                "createEvent must be an instance method");
    }
}
