package com.team10.uber.driver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ObserverSourceScanTest {

    @Test
    void noEventListenerAnnotation_inServiceClasses() throws Exception {
        Path serviceDir = Path.of("src/main/java/com/team10/uber/driver/service");
        assertTrue(Files.exists(serviceDir), "Service directory must exist");

        List<Path> serviceFiles = Files.walk(serviceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

        assertFalse(serviceFiles.isEmpty(), "Service directory must contain .java files");

        for (Path file : serviceFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("@EventListener"),
                    file.getFileName() + " must not use @EventListener — use the Observer pattern instead");
        }
    }
}
