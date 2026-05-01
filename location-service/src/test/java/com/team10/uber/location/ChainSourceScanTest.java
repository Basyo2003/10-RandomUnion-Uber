package com.team10.uber.location;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChainSourceScanTest {

    @Test
    void doFilterInternal_delegatesToChain_notInline() throws Exception {
        // location-service uses JwtAuthFilter (not JwtAuthenticationFilter)
        Path path = Path.of(
                "src/main/java/com/team10/uber/location/security/JwtAuthFilter.java");
        assertTrue(Files.exists(path), "JwtAuthFilter.java must exist");

        String source = Files.readString(path);

        assertFalse(source.contains("parseSignedClaims"),
                "JwtAuthFilter.doFilterInternal() must not call parseSignedClaims inline — delegate to chain");
        assertFalse(source.contains("getHeader(\"Authorization\")"),
                "JwtAuthFilter.doFilterInternal() must not extract Authorization header inline — delegate to TokenExtractionHandler");
    }
}
