package com.team10.uber.driver.security;

import com.team10.uber.driver.security.chain.AuthContext;
import com.team10.uber.driver.security.chain.AuthHandler;
import com.team10.uber.driver.security.chain.RoleAuthorizationHandler;
import com.team10.uber.driver.security.chain.SignatureValidationHandler;
import com.team10.uber.driver.security.chain.TokenExtractionHandler;
import com.team10.uber.driver.security.chain.UserLoaderHandler;
import com.team10.uber.driver.singleton.JwtConfigurationManager;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JwtAuthChainTest {

    private static final String TEST_SECRET =
            "dVJlckp3dFNoYXJlZFNlY3JldEtleUZvckFsbEZpdmVTZXJ2aWNlczEyMzQ1Ng==";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtConfigurationManager.initConfig(TEST_SECRET, 86_400_000L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter();

        mockMvc = MockMvcBuilders.standaloneSetup(new AdminEndpointController(), new HealthController())
                .addFilter(filter, "/*")
                .build();
    }

    // ── Test a) AuthHandler exists with required methods ─────────────────────
    @Test
    void authHandlerExistsWithRequiredMethods() throws Exception {
        Class<?> clazz = Class.forName("com.team10.uber.driver.security.chain.AuthHandler");
        assertNotNull(clazz.getDeclaredMethod("setNext", clazz));
        assertNotNull(clazz.getDeclaredMethod("handle",
                Class.forName("com.team10.uber.driver.security.chain.AuthContext")));
    }

    // ── Test b) At least 3 concrete subclasses of AuthHandler exist ──────────
    @Test
    void atLeastThreeConcreteHandlersExist() throws Exception {
        Class<?> base = Class.forName("com.team10.uber.driver.security.chain.AuthHandler");
        String[] handlers = {
            "com.team10.uber.driver.security.chain.TokenExtractionHandler",
            "com.team10.uber.driver.security.chain.SignatureValidationHandler",
            "com.team10.uber.driver.security.chain.UserLoaderHandler",
            "com.team10.uber.driver.security.chain.RoleAuthorizationHandler"
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
        mockMvc.perform(get("/api/drivers/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Test d) Invalid token → 401 ──────────────────────────────────────────
    @Test
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/drivers/1")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // ── Test e) Valid token — JWT claims trusted, no DB check ─────────────────
    @Test
    void validToken_passesAuthChain() throws Exception {
        String token = generateToken("rider@test.com", 1L, "RIDER");

        // Auth passes (UserLoaderHandler trusts JWT claims, no DB lookup).
        // /api/drivers/1 is not registered in the mock — expect 404 not 401.
        mockMvc.perform(get("/api/drivers/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── Test f) RIDER token on ADMIN-only endpoint → 403 ─────────────────────
    @Test
    void riderToken_onAdminEndpoint_returns403() throws Exception {
        String token = generateToken("rider@test.com", 1L, "RIDER");

        mockMvc.perform(put("/api/users/1/role")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Test g) ADMIN token on ADMIN-only endpoint → 200 ─────────────────────
    @Test
    void adminToken_onAdminEndpoint_returns200() throws Exception {
        String token = generateToken("admin@test.com", 2L, "ADMIN");

        mockMvc.perform(put("/api/users/1/role")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
    }

    // ── Test h) doFilterInternal() does NOT contain inline JWT logic ──────────
    @Test
    void doFilterInternal_doesNotContainInlineJwtLogic() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/team10/uber/driver/security/JwtAuthenticationFilter.java"));
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

        AuthContext ctx = new AuthContext(new MockHttpServletRequest(), new MockHttpServletResponse());
        AuthContext result = handler.handle(ctx);

        assertFalse(result.isPassed());
        assertEquals(401, result.getErrorStatus());
        verify(mockNext, never()).handle(any());
    }

    // ── Chain unit test: full chain populates context on success ─────────────
    @Test
    void fullChain_withValidToken_populatesContextCorrectly() {
        AuthHandler head = new TokenExtractionHandler();
        AuthHandler sig  = new SignatureValidationHandler();
        AuthHandler user = new UserLoaderHandler();
        AuthHandler role = new RoleAuthorizationHandler();
        head.setNext(sig);
        sig.setNext(user);
        user.setNext(role);

        String token = generateToken("rider@test.com", 5L, "RIDER");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        AuthContext ctx    = new AuthContext(request, new MockHttpServletResponse());
        AuthContext result = head.handle(ctx);

        assertTrue(result.isPassed());
        assertEquals("rider@test.com", result.getUserEmail());
        assertEquals("RIDER", result.getRole());
        assertEquals(5L, result.getUserId());
    }

    // ── Test i) /api/drivers/health requires no token (shouldNotFilter) ──────
    @Test
    void healthEndpoint_withNoToken_returns200() throws Exception {
        mockMvc.perform(get("/api/drivers/health"))
                .andExpect(status().isOk());
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

    @RestController
    static class HealthController {
        @GetMapping("/api/drivers/health")
        ResponseEntity<String> health() {
            return ResponseEntity.ok("OK");
        }
    }
}
