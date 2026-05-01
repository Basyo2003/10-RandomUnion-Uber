package com.team10.uber.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CC6ApplicationYmlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "../user-service/src/main/resources/application.yml",
            "../driver-service/src/main/resources/application.yml",
            "../ride-service/src/main/resources/application.yml",
            "../location-service/src/main/resources/application.yml",
            "../payment-service/src/main/resources/application.yml"
    })
    void applicationYml_exists_notProperties(String relativePath) {
        Path ymlPath = Path.of(relativePath);
        assertTrue(Files.exists(ymlPath),
                "application.yml must exist (not .properties) at: " + relativePath);
    }

    @Test
    void userService_applicationYml_hasRequiredKeys() throws Exception {
        String yml = Files.readString(Path.of("../user-service/src/main/resources/application.yml"));
        assertTrue(yml.contains("spring:"), "user-service application.yml must have 'spring:' key");
        assertTrue(yml.contains("datasource:"), "user-service application.yml must have 'datasource:' key");
        assertTrue(yml.contains("jwt:"), "user-service application.yml must have 'jwt:' key");
        assertTrue(yml.contains("secret:"), "user-service application.yml must have 'secret:' key");
        assertTrue(yml.contains("application:"), "user-service application.yml must have 'application:' key");
    }

    @Test
    void driverService_applicationYml_hasRequiredKeys() throws Exception {
        String yml = Files.readString(Path.of("../driver-service/src/main/resources/application.yml"));
        assertTrue(yml.contains("spring:"), "driver-service application.yml must have 'spring:' key");
        assertTrue(yml.contains("datasource:"), "driver-service application.yml must have 'datasource:' key");
        assertTrue(yml.contains("jwt:"), "driver-service application.yml must have 'jwt:' key");
        assertTrue(yml.contains("elasticsearch:"), "driver-service application.yml must have 'elasticsearch:' key");
    }

    @Test
    void rideService_applicationYml_hasRequiredKeys() throws Exception {
        String yml = Files.readString(Path.of("../ride-service/src/main/resources/application.yml"));
        assertTrue(yml.contains("spring:"), "ride-service application.yml must have 'spring:' key");
        assertTrue(yml.contains("datasource:"), "ride-service application.yml must have 'datasource:' key");
        assertTrue(yml.contains("jwt:"), "ride-service application.yml must have 'jwt:' key");
        assertTrue(yml.contains("neo4j:"), "ride-service application.yml must have 'neo4j:' key");
    }

    @Test
    void locationService_applicationYml_hasRequiredKeys() throws Exception {
        String yml = Files.readString(Path.of("../location-service/src/main/resources/application.yml"));
        assertTrue(yml.contains("spring:"), "location-service application.yml must have 'spring:' key");
        assertTrue(yml.contains("datasource:"), "location-service application.yml must have 'datasource:' key");
        assertTrue(yml.contains("jwt:"), "location-service application.yml must have 'jwt:' key");
        assertTrue(yml.contains("cassandra:"), "location-service application.yml must have 'cassandra:' key");
    }

    @Test
    void paymentService_applicationYml_hasRequiredKeys() throws Exception {
        String yml = Files.readString(Path.of("../payment-service/src/main/resources/application.yml"));
        assertTrue(yml.contains("spring:"), "payment-service application.yml must have 'spring:' key");
        assertTrue(yml.contains("datasource:"), "payment-service application.yml must have 'datasource:' key");
        assertTrue(yml.contains("jwt:"), "payment-service application.yml must have 'jwt:' key");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../user-service/src/main/resources/application.properties",
            "../driver-service/src/main/resources/application.properties",
            "../ride-service/src/main/resources/application.properties",
            "../location-service/src/main/resources/application.properties",
            "../payment-service/src/main/resources/application.properties"
    })
    void applicationProperties_doesNotExist(String relativePath) {
        Path propertiesPath = Path.of(relativePath);
        assertFalse(Files.exists(propertiesPath),
                "application.properties must NOT exist — use application.yml instead: " + relativePath);
    }
}
