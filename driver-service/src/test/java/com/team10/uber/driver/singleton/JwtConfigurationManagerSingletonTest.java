package com.team10.uber.driver.singleton;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.*;

/**
 * M2 reflection test — verifies JwtConfigurationManager satisfies the Singleton pattern contract.
 */
class JwtConfigurationManagerSingletonTest {

    // ── Private constructor ───────────────────────────────────────────────────

    @Test
    @DisplayName("Constructor is private — cannot be instantiated externally")
    void constructor_isPrivate() throws Exception {
        Constructor<JwtConfigurationManager> ctor =
                JwtConfigurationManager.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers()))
                .as("JwtConfigurationManager constructor must be private")
                .isTrue();
    }

    // ── Public static getInstance() ───────────────────────────────────────────

    @Test
    @DisplayName("getInstance() is public and static")
    void getInstance_isPublicStatic() throws Exception {
        java.lang.reflect.Method method = JwtConfigurationManager.class.getDeclaredMethod("getInstance");
        assertThat(Modifier.isPublic(method.getModifiers()))
                .as("getInstance() must be public")
                .isTrue();
        assertThat(Modifier.isStatic(method.getModifiers()))
                .as("getInstance() must be static")
                .isTrue();
    }

    // ── Same reference on repeated calls ─────────────────────────────────────

    @Test
    @DisplayName("Two calls to getInstance() return the same reference")
    void getInstance_returnsSameReference() {
        JwtConfigurationManager ref1 = JwtConfigurationManager.getInstance();
        JwtConfigurationManager ref2 = JwtConfigurationManager.getInstance();

        assertThat(ref1).isSameAs(ref2);
    }

    // ── Not a Spring @Component ───────────────────────────────────────────────

    @Test
    @DisplayName("JwtConfigurationManager is NOT annotated with @Component")
    void notAnnotatedWithComponent() {
        boolean hasComponent = JwtConfigurationManager.class.isAnnotationPresent(
                org.springframework.stereotype.Component.class);
        assertThat(hasComponent)
                .as("JwtConfigurationManager must NOT be a Spring @Component — it is a manual Singleton")
                .isFalse();
    }

    // ── Thread-safety: double-checked locking ─────────────────────────────────

    @Test
    @DisplayName("Concurrent getInstance() calls all return the same reference")
    void getInstance_threadSafe() throws Exception {
        JwtConfigurationManager[] results = new JwtConfigurationManager[10];
        Thread[] threads = new Thread[10];

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> results[idx] = JwtConfigurationManager.getInstance());
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        JwtConfigurationManager first = results[0];
        for (JwtConfigurationManager m : results) {
            assertThat(m).isSameAs(first);
        }
    }

    // ── initConfig does not break singleton ───────────────────────────────────

    @Test
    @DisplayName("initConfig — configures the singleton in place (does not replace instance)")
    void initConfig_configuresSingletonInPlace() {
        JwtConfigurationManager before = JwtConfigurationManager.getInstance();
        JwtConfigurationManager.initConfig(
                "dVJlckp3dFNoYXJlZFNlY3JldEtleUZvckFsbEZpdmVTZXJ2aWNlczEyMzQ1Ng==",
                86_400_000L);
        JwtConfigurationManager after = JwtConfigurationManager.getInstance();

        assertThat(before).isSameAs(after);
        assertThat(after.getSecretKey()).isNotNull();
        assertThat(after.getExpirationMs()).isEqualTo(86_400_000L);
    }
}
