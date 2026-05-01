package com.team10.uber.ride;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class SingletonReflectionTest {

    private static final String CLASS_NAME = "com.team10.uber.ride.singleton.JwtConfigurationManager";

    @Test
    void jwtConfigurationManager_hasPrivateConstructor() throws Exception {
        Class<?> clazz = Class.forName(CLASS_NAME);
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        assertEquals(1, constructors.length, "JwtConfigurationManager must have exactly 1 constructor");
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()),
                "JwtConfigurationManager constructor must be private");
    }

    @Test
    void getInstance_isPublicStatic_returnsCorrectType() throws Exception {
        Class<?> clazz = Class.forName(CLASS_NAME);
        Method getInstance = clazz.getMethod("getInstance");
        assertTrue(Modifier.isPublic(getInstance.getModifiers()), "getInstance() must be public");
        assertTrue(Modifier.isStatic(getInstance.getModifiers()), "getInstance() must be static");
        assertEquals(clazz, getInstance.getReturnType(), "getInstance() must return JwtConfigurationManager");
    }

    @Test
    void getInstance_returnsSameReference() throws Exception {
        Class<?> clazz = Class.forName(CLASS_NAME);
        Method getInstance = clazz.getMethod("getInstance");
        Object instance1 = getInstance.invoke(null);
        Object instance2 = getInstance.invoke(null);
        assertSame(instance1, instance2, "getInstance() must return the same reference on repeated calls");
    }

    @Test
    void getInstance_threadSafe_10Threads() throws Exception {
        Class<?> clazz = Class.forName(CLASS_NAME);
        Method getInstance = clazz.getMethod("getInstance");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> getInstance.invoke(null)));
        }

        executor.shutdown();
        Object first = futures.get(0).get();
        for (Future<Object> future : futures) {
            assertSame(first, future.get(), "All threads must get the same JwtConfigurationManager instance");
        }
    }

    @Test
    void jwtConfigurationManager_notAnnotatedWithSpringStereotype() throws Exception {
        Class<?> clazz = Class.forName(CLASS_NAME);
        assertNull(clazz.getAnnotation(Component.class), "JwtConfigurationManager must not be @Component");
        assertNull(clazz.getAnnotation(Service.class), "JwtConfigurationManager must not be @Service");
        assertNull(clazz.getAnnotation(Configuration.class), "JwtConfigurationManager must not be @Configuration");
    }
}
