package com.team10.uber.ride.security;

import com.team10.uber.ride.security.chain.AuthContext;
import com.team10.uber.ride.security.chain.AuthHandler;
import com.team10.uber.ride.security.chain.RoleAuthorizationHandler;
import com.team10.uber.ride.security.chain.SignatureValidationHandler;
import com.team10.uber.ride.security.chain.TokenExtractionHandler;
import com.team10.uber.ride.security.chain.UserLoaderHandler;
import com.team10.uber.ride.singleton.JwtConfigurationManager;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
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

    private JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtConfigurationManager.initConfig(TEST_SECRET, 86_400_000L);
        jdbcTemplate = mock(JdbcTemplate.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jdbcTemplate);

        mockMvc = MockMvcBuilders.standaloneSetup(new AdminEndpointController())
                .addFilter(filter, "/*")
                .build();
    }

    @Test
    void authHandlerExistsWithRequiredMethods() throws Exception {
        Class<?> clazz = Class.forName("com.team10.uber.ride.security.chain.AuthHandler");
        assertNotNull(clazz.getDeclaredMethod("setNext", clazz));
        assertNotNull(clazz.getDeclaredMethod("handle",
                Class.forName("com.team10.uber.ride.security.chain.AuthContext")));
    }

    @Test
    void atLeastThreeConcreteHandlersExist() throws Exception {
        Class<?> base = Class.forName("com.team10.uber.ride.security.chain.AuthHandler");
        String[] handlers = {
            "com.team10.uber.ride.security.chain.TokenExtractionHandler",
            "com.team10.uber.ride.security.chain.SignatureValidationHandler",
            "com.team10.uber.ride.security.chain.UserLoaderHandler",
            "com.team10.uber.ride.security.chain.RoleAuthorizationHandler"
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

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/rides/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/rides/1")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedUser_validToken_returns401() throws Exception {
        String token = generateToken("deleted@test.com", 99L, "RIDER");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(99L), eq("deleted@test.com")))
                .thenReturn(0);

        mockMvc.perform(get("/api/rides/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void riderToken_onAdminEndpoint_returns403() throws Exception {
        String token = generateToken("rider@test.com", 1L, "RIDER");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L), eq("rider@test.com")))
                .thenReturn(1);

        mockMvc.perform(put("/api/users/1/role")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminToken_onAdminEndpoint_returns200() throws Exception {
        String token = generateToken("admin@test.com", 2L, "ADMIN");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(2L), eq("admin@test.com")))
                .thenReturn(1);

        mockMvc.perform(put("/api/users/1/role")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void doFilterInternal_doesNotContainInlineJwtLogic() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/team10/uber/ride/security/JwtAuthenticationFilter.java"));
        assertFalse(source.contains("parseSignedClaims"),
                "doFilterInternal must not parse JWT inline — use the chain");
        assertFalse(source.contains("getHeader(\"Authorization\")"),
                "doFilterInternal must not extract header inline — use TokenExtractionHandler");
    }

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

    @Test
    void fullChain_withValidToken_populatesContextCorrectly() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(5L), eq("rider@test.com")))
                .thenReturn(1);

        AuthHandler head = new TokenExtractionHandler();
        AuthHandler sig  = new SignatureValidationHandler();
        AuthHandler user = new UserLoaderHandler(jdbcTemplate);
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
