package com.team10.uber.ride;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FactorySourceScanTest {

    @Test
    void serviceClasses_doNotDirectlyInstantiateEvents() throws Exception {
        Path serviceDir = Path.of("src/main/java/com/team10/uber/ride/service");
        assertTrue(Files.exists(serviceDir), "Service directory must exist");

        List<Path> serviceFiles = Files.walk(serviceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

        assertFalse(serviceFiles.isEmpty(), "Service directory must contain .java files");

        for (Path file : serviceFiles) {
            String source = Files.readString(file);
            String fileName = file.getFileName().toString();
            assertFalse(source.contains("new AuthEvent("),
                    fileName + " must not directly instantiate AuthEvent — use EventFactory");
            assertFalse(source.contains("new DriverEvent("),
                    fileName + " must not directly instantiate DriverEvent — use EventFactory");
            assertFalse(source.contains("new RideEvent("),
                    fileName + " must not directly instantiate RideEvent — use EventFactory");
            assertFalse(source.contains("new LocationEvent("),
                    fileName + " must not directly instantiate LocationEvent — use EventFactory");
            assertFalse(source.contains("new PaymentAuditEvent("),
                    fileName + " must not directly instantiate PaymentAuditEvent — use EventFactory");
        }
    }
}
