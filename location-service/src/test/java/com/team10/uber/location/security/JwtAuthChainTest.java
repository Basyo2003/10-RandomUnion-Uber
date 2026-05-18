package com.team10.uber.location.security;

import com.team10.uber.location.security.chain.AuthContext;
import com.team10.uber.location.security.chain.AuthHandler;
import com.team10.uber.location.security.chain.AuthResult;
import com.team10.uber.location.security.chain.RoleAuthorizationHandler;
import com.team10.uber.location.security.chain.SignatureValidationHandler;
import com.team10.uber.location.security.chain.TokenExtractionHandler;
import com.team10.uber.location.security.chain.UserLoaderHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JwtAuthChainTest {

    private static final String TEST_SECRET =
            "dVJlckp3dFNoYXJlZFNlY3JldEtleUZvckFsbEZpdmVTZXJ2aWNlczEyMzQ1Ng==";

    private JwtService jwtService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);

        TokenExtractionHandler    tokenHandler = new TokenExtractionHandler();
        SignatureValidationHandler sigHandler   = new SignatureValidationHandler(jwtService);
        UserLoaderHandler          userHandler  = new UserLoaderHandler();
        RoleAuthorizationHandler   roleHandler  = new RoleAuthorizationHandler();

        JwtAuthFilter filter = new JwtAuthFilter(tokenHandler, sigHandler, userHandler, roleHandler);

        mockMvc = MockMvcBuilders.standaloneSetup(new AdminEndpointController())
                .addFilter(filter, "/*")
                .build();
    }

    // ── Test a) AuthHandler exists with required methods ─────────────────────
    @Test
    void authHandlerExistsWithRequiredMethods() throws Exception {
        Class<?> clazz = Class.forName("com.team10.uber.location.security.chain.AuthHandler");
        assertNotNull(clazz.getDeclaredMethod("setNext", clazz));
        assertNotNull(clazz.getDeclaredMethod("handle",
                Class.forName("com.team10.uber.location.security.chain.AuthContext")));
    }

    // ── Test b) At least 3 concrete subclasses of AuthHandler exist ──────────
    @Test
    void atLeastThreeConcreteHandlersExist() throws Exception {
        Class<?> base = Class.forName("com.team10.uber.location.security.chain.AuthHandler");
        String[] handlers = {
            "com.team10.uber.location.security.chain.TokenExtractionHandler",
            "com.team10.uber.location.security.chain.SignatureValidationHandler",
            "com.team10.uber.location.security.chain.UserLoaderHandler",
            "com.team10.uber.location.security.chain.RoleAuthorizationHandler"
        };
        int found = 0;
        for (String name : handlers) {
            try {
                Class<?> h = Class.forName(name);
                if (base.isAssignableFrom(h)) found++;
            } catch (ClassNotFoundException ignored) {}
        }
        assertTrue(found >= 3, "Expected ≥3 concrete AuthHandler subclasses, found: " + found);
    }

    // ── Test c) Missing Authorization header → 401 ───────────────────────────
    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/locations/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Test d) Invalid token → 401 ──────────────────────────────────────────
    @Test
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/locations/1")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // ── Test e) Valid signature but missing uid claim → 401 ──────────────────
    // UserLoaderHandler trusts JWT claims (no DB lookup); it rejects tokens
    // that are missing the uid or email fields required to build a security context.
    @Test
    void missingUidClaim_validToken_returns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET));
        String token = Jwts.builder()
                .subject("nouid@test.com")
                .claim("role", "RIDER")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/locations/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ── Test f) RIDER role fails when required role is ADMIN ─────────────────
    @Test
    void riderRole_failsWhenAdminRequired() {
        AuthContext ctx = new AuthContext(new MockHttpServletRequest());
        ctx.setRole("RIDER");
        ctx.setRequiredRole("ADMIN");

        RoleAuthorizationHandler handler = new RoleAuthorizationHandler();
        AuthResult result = handler.handle(ctx);

        assertFalse(result.isSuccess());
        assertEquals(403, result.getStatus());
    }

    // ── Test g) ADMIN role passes when required role is ADMIN ────────────────
    @Test
    void adminRole_passesWhenAdminRequired() {
        AuthContext ctx = new AuthContext(new MockHttpServletRequest());
        ctx.setRole("ADMIN");
        ctx.setRequiredRole("ADMIN");

        RoleAuthorizationHandler handler = new RoleAuthorizationHandler();
        AuthResult result = handler.handle(ctx);

        assertTrue(result.isSuccess());
    }

    // ── Test h) doFilterInternal() does NOT contain inline JWT logic ──────────
    @Test
    void doFilterInternal_doesNotContainInlineJwtLogic() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/team10/uber/location/security/JwtAuthFilter.java"));
        assertFalse(source.contains("parseSignedClaims"),
                "doFilterInternal must not parse JWT inline — use the chain");
        assertFalse(source.contains("getHeader(\"Authorization\")"),
                "doFilterInternal must not extract header inline — use TokenExtractionHandler");
    }

    // ── Chain unit test: short-circuit stops next handler ────────────────────
    @Test
    void tokenExtractionHandler_missingToken_doesNotCallNext() {
        AuthHandler mockNext = mock(AuthHandler.class);
        TokenExtractionHandler handler = new TokenExtractionHandler();
        handler.setNext(mockNext);

        AuthContext ctx    = new AuthContext(new MockHttpServletRequest());
        AuthResult  result = handler.handle(ctx);

        assertFalse(result.isSuccess());
        assertEquals(401, result.getStatus());
        verify(mockNext, never()).handle(any());
    }

    // ── Chain unit test: full chain populates context on success ─────────────
    @Test
    void fullChain_withValidToken_populatesContextCorrectly() {
        TokenExtractionHandler    tokenHandler = new TokenExtractionHandler();
        SignatureValidationHandler sigHandler   = new SignatureValidationHandler(jwtService);
        UserLoaderHandler          userHandler  = new UserLoaderHandler();
        RoleAuthorizationHandler   roleHandler  = new RoleAuthorizationHandler();
        tokenHandler.setNext(sigHandler);
        sigHandler.setNext(userHandler);
        userHandler.setNext(roleHandler);

        String token = generateToken("rider@test.com", 5L, "RIDER");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        AuthContext ctx    = new AuthContext(request);
        AuthResult  result = tokenHandler.handle(ctx);

        assertTrue(result.isSuccess());
        assertEquals("rider@test.com", ctx.getEmail());
        assertEquals("RIDER", ctx.getRole());
        assertEquals(5L, ctx.getUserId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private String generateToken(String email, Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET));
        return Jwts.builder()
                .subject(email)
                .claim("uid", userId)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key)
                .compact();
    }

    @RestController
    static class AdminEndpointController {
        @PutMapping("/api/users/{id}/role")
        ResponseEntity<Void> changeRole(@PathVariable Long id) {
            return ResponseEntity.ok().build();
        }
    }
}
