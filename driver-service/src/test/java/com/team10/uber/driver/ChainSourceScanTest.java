package com.team10.uber.driver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChainSourceScanTest {

    @Test
    void doFilterInternal_delegatesToChain_notInline() throws Exception {
        Path path = Path.of(
                "src/main/java/com/team10/uber/driver/security/JwtAuthenticationFilter.java");
        assertTrue(Files.exists(path), "JwtAuthenticationFilter.java must exist");

        String source = Files.readString(path);

        assertFalse(source.contains("parseSignedClaims"),
                "JwtAuthenticationFilter.doFilterInternal() must not call parseSignedClaims inline — delegate to chain");
        assertFalse(source.contains("getHeader(\"Authorization\")"),
                "JwtAuthenticationFilter.doFilterInternal() must not extract Authorization header inline — delegate to TokenExtractionHandler");
    }
}
