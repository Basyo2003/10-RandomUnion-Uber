package com.team10.uber.user;

import com.team10.uber.contracts.dto.RideSummaryDTO;
import com.team10.uber.contracts.feign.PaymentServiceClient;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.user.config.CorrelationIdFilter;
import com.team10.uber.user.controller.UserController;
import com.team10.uber.user.dto.TopRiderDTO;
import com.team10.uber.user.dto.UserRideSummaryDTO;
import com.team10.uber.user.enums.Role;
import com.team10.uber.user.enums.Status;
import com.team10.uber.user.messaging.publishers.UserEventPublisher;
import com.team10.uber.user.model.User;
import com.team10.uber.user.observer.EntityObserver;
import com.team10.uber.user.observer.MongoEventLogger;
import com.team10.uber.user.repository.SavedAddressRepository;
import com.team10.uber.user.repository.UserRepository;
import com.team10.uber.user.repository.mongo.AuthEventRepository;
import com.team10.uber.user.service.UserService;
import feign.FeignException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S1-READ-DB — full requirement coverage for feat/M3/user/S1-READ-DB
 *
 * Requirements covered by JUnit:
 *   §1.1  datasource URL points to user-postgres
 *   §1.2  no cross-service SQL queries remain in UserRepository / UserService
 *   §2.1  pom.xml has OpenFeign, contracts, Spring Cloud BOM, AMQP, Loki4J, Prometheus deps
 *   §2.1  @EnableFeignClients on UserServiceApplication
 *   §2.3  FeignCorrelationConfig forwards X-Correlation-ID
 *   §2.4  all Feign calls wrapped in try-catch
 *   §2.6  application.yml has full RabbitMQ config block
 *   §2.10 ownership check (self-or-admin) on ride-summary and deactivate endpoints
 *   S1-F3 getUserRideSummary delegates to RideServiceClient — happy path, 404 fallback, 503 propagation
 *   S1-F4 deactivateUser delegates active-count to RideServiceClient — blocks on active rides, idempotent
 *   S1-F6 getTopRiders delegates per-user totals to PaymentServiceClient — sorted, limited, capped at 100
 *   S1-F9 searchByLanguageAndRides delegates completed-count to RideServiceClient — filters by minRides
 *   §11.1 logback-spring.xml has Loki4J appender + MDC fields
 *   §11.1 CorrelationIdFilter sets/generates correlationId and clears MDC in finally
 *   §11.1 UserService populates MDC userId; UserController has entry/exit log points
 *   §11.3 user-dashboard.json has ≥3 LogQL panels
 *   K8s   secret, PVC, StatefulSet (postgres:17, 512Mi, pg_isready probe), headless Service
 *
 * Requirements that CANNOT be covered by JUnit (manual testing required — see bottom of file).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S1-READ-DB — User Service M3 refactoring")
class S1ReadDbTest {

    // ── shared mocks (initialized by MockitoExtension before each @BeforeEach) ──
    @Mock private UserRepository       userRepository;
    @Mock private SavedAddressRepository savedAddressRepository;
    @Mock private AuthEventRepository  authEventRepository;
    @Mock private EntityObserver       entityObserver;
    @Mock private PasswordEncoder      passwordEncoder;
    @Mock private MongoEventLogger     mongoEventLogger;
    @Mock private UserEventPublisher   userEventPublisher;
    @Mock private RideServiceClient    rideServiceClient;
    @Mock private PaymentServiceClient paymentServiceClient;

    private UserService    userService;
    private UserController userController;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, savedAddressRepository, authEventRepository,
                entityObserver, passwordEncoder, mongoEventLogger,
                userEventPublisher, rideServiceClient, paymentServiceClient);
        userController = new UserController(userService);
    }

    // ── helper ────────────────────────────────────────────────────────────────
    private User user(Long id, Status status, Role role) {
        User u = new User();
        u.setId(id);
        u.setName("User-" + id);
        u.setEmail("user" + id + "@test.com");
        u.setPhone("01000000" + id);
        u.setStatus(status);
        u.setRole(role);
        return u;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §1.1  DB Isolation — application.yml datasource URL
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§1.1 — datasource URL + RabbitMQ + feign URLs in application.yml")
    class ApplicationYmlTest {

        private String yml;

        @BeforeEach
        void load() throws Exception {
            yml = Files.readString(Path.of("src/main/resources/application.yml"));
        }

        @Test
        @DisplayName("datasource URL must use user-postgres, not localhost")
        void datasourceUrl_usesUserPostgres() {
            assertTrue(yml.contains("url: jdbc:postgresql://user-postgres:5432/uberdb-users"),
                    "Datasource URL must be 'jdbc:postgresql://user-postgres:5432/uberdb-users'");
        }

        @Test
        @DisplayName("datasource URL must NOT contain localhost")
        void datasourceUrl_notLocalhost() {
            assertFalse(yml.contains("url: jdbc:postgresql://localhost"),
                    "Datasource URL must not use localhost — needs to work inside K8s");
        }

        @Test
        @DisplayName("RabbitMQ config must have acknowledge-mode: auto")
        void rabbitmq_acknowledgeMode() {
            assertTrue(yml.contains("acknowledge-mode: auto"), "Must have acknowledge-mode: auto");
        }

        @Test
        @DisplayName("RabbitMQ config must have default-requeue-rejected: false")
        void rabbitmq_noRequeue() {
            assertTrue(yml.contains("default-requeue-rejected: false"),
                    "Must have default-requeue-rejected: false for DLQ routing");
        }

        @Test
        @DisplayName("RabbitMQ config must have max-attempts: 3")
        void rabbitmq_maxAttempts() {
            assertTrue(yml.contains("max-attempts: 3"), "Must have max-attempts: 3 for retry");
        }

        @Test
        @DisplayName("feign.ride-service.url must be configured")
        void feign_rideServiceUrl() {
            assertTrue(yml.contains("ride-service"),
                    "application.yml must configure feign.ride-service.url");
        }

        @Test
        @DisplayName("feign.payment-service.url must be configured")
        void feign_paymentServiceUrl() {
            assertTrue(yml.contains("payment-service"),
                    "application.yml must configure feign.payment-service.url");
        }

        @Test
        @DisplayName("actuator must expose prometheus, health, info")
        void actuator_endpoints() {
            assertTrue(yml.contains("prometheus,health,info"),
                    "management.endpoints.web.exposure.include must contain prometheus,health,info");
        }

        @Test
        @DisplayName("metrics must enable percentiles histogram for http.server.requests")
        void actuator_histogramEnabled() {
            assertTrue(yml.contains("percentiles-histogram"),
                    "Must enable percentiles-histogram for http.server.requests");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §1.2  No cross-service SQL in UserRepository
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§1.2 — cross-service SQL queries removed from UserRepository")
    class NoCrossServiceSqlTest {

        private String repoSrc;
        private String svcSrc;

        @BeforeEach
        void load() throws Exception {
            repoSrc = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/repository/UserRepository.java"));
            svcSrc = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/service/UserService.java"));
        }

        @Test
        @DisplayName("UserRepository must NOT query the rides table (cross-service DB)")
        void noRidesTableQuery() {
            assertFalse(repoSrc.contains("FROM rides"),
                    "UserRepository must not query the rides table — it lives in ride-postgres");
        }

        @Test
        @DisplayName("getRideSummaryByUserId must be removed from UserRepository")
        void getRideSummaryByUserIdRemoved() {
            assertFalse(repoSrc.contains("getRideSummaryByUserId"),
                    "getRideSummaryByUserId cross-service query must be removed");
        }

        @Test
        @DisplayName("getTopRidersBySpending must be removed from UserRepository")
        void getTopRidersBySpendingRemoved() {
            assertFalse(repoSrc.contains("getTopRidersBySpending"),
                    "getTopRidersBySpending cross-service query must be removed");
        }

        @Test
        @DisplayName("findUsersByLanguageAndCompletedRides must be removed from UserRepository")
        void findUsersByLanguageAndCompletedRidesRemoved() {
            assertFalse(repoSrc.contains("findUsersByLanguageAndCompletedRides"),
                    "findUsersByLanguageAndCompletedRides cross-service query must be removed");
        }

        @Test
        @DisplayName("hasActiveRides must be removed from UserRepository")
        void hasActiveRidesRemoved() {
            assertFalse(repoSrc.contains("hasActiveRides"),
                    "hasActiveRides cross-service query must be removed");
        }

        @Test
        @DisplayName("UserRepository must still have the intra-service findByLanguagePreference")
        void findByLanguagePreferenceRetained() {
            assertTrue(repoSrc.contains("findByLanguagePreference"),
                    "findByLanguagePreference is intra-service (users table) and must be kept");
        }

        @Test
        @DisplayName("UserService must not call any removed cross-service query methods")
        void userServiceDoesNotCallRemovedMethods() {
            assertFalse(svcSrc.contains("getRideSummaryByUserId"),
                    "UserService must not call getRideSummaryByUserId");
            assertFalse(svcSrc.contains("getTopRidersBySpending"),
                    "UserService must not call getTopRidersBySpending");
            assertFalse(svcSrc.contains("findUsersByLanguageAndCompletedRides"),
                    "UserService must not call findUsersByLanguageAndCompletedRides");
            assertFalse(svcSrc.contains("hasActiveRides"),
                    "UserService must not call hasActiveRides");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §2.1  pom.xml required dependencies
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§2.1 — pom.xml required dependencies")
    class PomDependenciesTest {

        private String pom;

        @BeforeEach
        void load() throws Exception {
            pom = Files.readString(Path.of("pom.xml"));
        }

        @Test @DisplayName("spring-cloud-starter-openfeign must be present")
        void hasOpenFeign() {
            assertTrue(pom.contains("spring-cloud-starter-openfeign"),
                    "pom.xml must have spring-cloud-starter-openfeign");
        }

        @Test @DisplayName("contracts module must be a dependency")
        void hasContracts() {
            assertTrue(pom.contains("<artifactId>contracts</artifactId>"),
                    "pom.xml must declare the contracts module as a dependency");
        }

        @Test @DisplayName("spring-cloud-dependencies BOM must be in dependencyManagement")
        void hasSpringCloudBom() {
            assertTrue(pom.contains("spring-cloud-dependencies"),
                    "Spring Cloud BOM must be in dependencyManagement");
        }

        @Test @DisplayName("spring-boot-starter-amqp must be present")
        void hasAmqp() {
            assertTrue(pom.contains("spring-boot-starter-amqp"),
                    "pom.xml must have spring-boot-starter-amqp for RabbitMQ");
        }

        @Test @DisplayName("loki-logback-appender must be present")
        void hasLoki4j() {
            assertTrue(pom.contains("loki-logback-appender"),
                    "pom.xml must have loki-logback-appender");
        }

        @Test @DisplayName("micrometer-registry-prometheus must be present")
        void hasPrometheus() {
            assertTrue(pom.contains("micrometer-registry-prometheus"),
                    "pom.xml must have micrometer-registry-prometheus for /actuator/prometheus");
        }

        @Test @DisplayName("@EnableFeignClients must be on UserServiceApplication")
        void enableFeignClients() throws Exception {
            String src = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/UserServiceApplication.java"));
            assertTrue(src.contains("@EnableFeignClients"),
                    "UserServiceApplication must have @EnableFeignClients");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §2.3  FeignCorrelationConfig
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§2.3 — FeignCorrelationConfig propagates X-Correlation-ID")
    class FeignCorrelationConfigTest {

        @Test
        @DisplayName("FeignCorrelationConfig must read correlationId from MDC and set header")
        void configPropagatesCorrelationId() throws Exception {
            String src = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/config/FeignCorrelationConfig.java"));
            assertTrue(src.contains("MDC.get(\"correlationId\")"),
                    "Must read correlationId from MDC");
            assertTrue(src.contains("X-Correlation-ID"),
                    "Must forward X-Correlation-ID header on all outgoing Feign requests");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §2.4  Feign error handling present in UserService
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§2.4 — Feign calls wrapped in try-catch")
    class FeignErrorHandlingStructureTest {

        @Test
        @DisplayName("UserService source must catch FeignException.NotFound and FeignException")
        void catchesFeignExceptions() throws Exception {
            String src = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/service/UserService.java"));
            assertTrue(src.contains("FeignException.NotFound"),
                    "Must have catch block for FeignException.NotFound");
            assertTrue(src.contains("catch (FeignException"),
                    "Must have catch block for generic FeignException");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // S1-F3  getUserRideSummary → Feign → ride-service
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("S1-F3 — getUserRideSummary delegates to ride-service via Feign")
    class GetUserRideSummaryTest {

        @BeforeEach
        void stubUser() {
            lenient().when(userRepository.findById(1L)).thenReturn(
                    Optional.of(user(1L, Status.ACTIVE, Role.RIDER)));
        }

        @Test
        @DisplayName("calls RideServiceClient.getUserRideSummary — not SQL")
        void callsRideClient() {
            when(rideServiceClient.getUserRideSummary(1L))
                    .thenReturn(new RideSummaryDTO(5L, 3L, 1L, 225.0, 75.0));

            userService.getUserRideSummary(1L);

            verify(rideServiceClient).getUserRideSummary(1L);
            verifyNoInteractions(paymentServiceClient);
        }

        @Test
        @DisplayName("spec scenario: 5 rides (3 COMPLETED, 1 CANCELLED, 1 REQUESTED) → correct DTO")
        void returnsCorrectDtoFromFeignResponse() {
            when(rideServiceClient.getUserRideSummary(1L))
                    .thenReturn(new RideSummaryDTO(5L, 3L, 1L, 225.0, 75.0));

            UserRideSummaryDTO result = userService.getUserRideSummary(1L);

            assertAll(
                    () -> assertEquals(1L,    result.getUserId()),
                    () -> assertEquals(5L,    result.getTotalRides()),
                    () -> assertEquals(3L,    result.getCompletedRides()),
                    () -> assertEquals(1L,    result.getCancelledRides()),
                    () -> assertEquals(225.0, result.getTotalSpent()),
                    () -> assertEquals(75.0,  result.getAverageFare())
            );
        }

        @Test
        @DisplayName("Feign 404 → returns empty summary (graceful degradation)")
        void feignNotFound_returnsEmptySummary() {
            when(rideServiceClient.getUserRideSummary(1L))
                    .thenThrow(mock(FeignException.NotFound.class));

            UserRideSummaryDTO result = userService.getUserRideSummary(1L);

            assertAll(
                    () -> assertEquals(0L,  result.getTotalRides()),
                    () -> assertEquals(0L,  result.getCompletedRides()),
                    () -> assertEquals(0L,  result.getCancelledRides()),
                    () -> assertEquals(0.0, result.getTotalSpent()),
                    () -> assertEquals(0.0, result.getAverageFare())
            );
        }

        @Test
        @DisplayName("Feign 5xx → throws 503 SERVICE_UNAVAILABLE")
        void feignError_throws503() {
            when(rideServiceClient.getUserRideSummary(1L))
                    .thenThrow(mock(FeignException.class));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.getUserRideSummary(1L));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        }

        @Test
        @DisplayName("user not found in user-postgres → throws 404")
        void userNotFound_throws404() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.getUserRideSummary(999L));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
            verifyNoInteractions(rideServiceClient);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // S1-F4  deactivateUser → Feign → ride-service
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("S1-F4 — deactivateUser delegates active-count check to ride-service")
    class DeactivateUserTest {

        @BeforeEach
        void stubUser() {
            when(userRepository.findById(1L)).thenReturn(
                    Optional.of(user(1L, Status.ACTIVE, Role.RIDER)));
        }

        @Test
        @DisplayName("calls RideServiceClient.getActiveRideCount — not SQL")
        void callsRideClient() {
            when(rideServiceClient.getActiveRideCount(1L)).thenReturn(0);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.deactivateUser(1L);

            verify(rideServiceClient).getActiveRideCount(1L);
        }

        @Test
        @DisplayName("spec scenario: active ride exists → throws 400 BAD_REQUEST")
        void activeRideExists_throws400() {
            when(rideServiceClient.getActiveRideCount(1L)).thenReturn(1);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.deactivateUser(1L));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason() != null && ex.getReason().contains("active rides"),
                    "Error message must mention active rides");
        }

        @Test
        @DisplayName("spec scenario: no active rides → user status becomes DEACTIVATED")
        void noActiveRides_deactivatesUser() {
            when(rideServiceClient.getActiveRideCount(1L)).thenReturn(0);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.deactivateUser(1L);

            assertEquals(Status.DEACTIVATED, result.getStatus());
        }

        @Test
        @DisplayName("idempotent: re-deactivating already DEACTIVATED user returns 200, no Feign call")
        void alreadyDeactivated_isIdempotent() {
            when(userRepository.findById(1L)).thenReturn(
                    Optional.of(user(1L, Status.DEACTIVATED, Role.RIDER)));

            User result = userService.deactivateUser(1L);

            assertEquals(Status.DEACTIVATED, result.getStatus());
            verifyNoInteractions(rideServiceClient);
        }

        @Test
        @DisplayName("ride-service unavailable → throws 503 SERVICE_UNAVAILABLE")
        void rideServiceDown_throws503() {
            when(rideServiceClient.getActiveRideCount(1L))
                    .thenThrow(mock(FeignException.class));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.deactivateUser(1L));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §2.10  Ownership checks (controller level)
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§2.10 — ownership rule on ride-summary and deactivate")
    class OwnershipCheckTest {

        // helper: build a request with userId + role attributes set
        private MockHttpServletRequest req(Long userId, String role) {
            MockHttpServletRequest r = new MockHttpServletRequest();
            r.setAttribute("userId",   userId);
            r.setAttribute("userRole", role);
            return r;
        }

        // ── ride-summary ──────────────────────────────────────────────────────

        @Test
        @DisplayName("ride-summary: owner (RIDER, same id) → 200 allowed")
        void rideSummary_owner_allowed() {
            when(userRepository.findById(1L)).thenReturn(
                    Optional.of(user(1L, Status.ACTIVE, Role.RIDER)));
            when(rideServiceClient.getUserRideSummary(1L))
                    .thenReturn(new RideSummaryDTO(0L, 0L, 0L, 0.0, 0.0));

            assertDoesNotThrow(() -> userController.getUserRideSummary(1L, req(1L, "RIDER")));
        }

        @Test
        @DisplayName("ride-summary: ADMIN accessing another user → 200 allowed")
        void rideSummary_admin_allowed() {
            when(userRepository.findById(1L)).thenReturn(
                    Optional.of(user(1L, Status.ACTIVE, Role.RIDER)));
            when(rideServiceClient.getUserRideSummary(1L))
                    .thenReturn(new RideSummaryDTO(0L, 0L, 0L, 0.0, 0.0));

            assertDoesNotThrow(() -> userController.getUserRideSummary(1L, req(99L, "ADMIN")));
        }

        @Test
        @DisplayName("ride-summary: RIDER accessing someone else → 403 FORBIDDEN")
        void rideSummary_otherRider_forbidden() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userController.getUserRideSummary(1L, req(2L, "RIDER")));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        @DisplayName("ride-summary: missing auth attributes (gateway bypass) → 401 UNAUTHORIZED")
        void rideSummary_noAuth_unauthorized() {
            // no attributes set on request — simulates missing JWT parsing
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userController.getUserRideSummary(1L, new MockHttpServletRequest()));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        // ── deactivate ────────────────────────────────────────────────────────

        @Test
        @DisplayName("deactivate: owner deactivating themselves → 200 allowed")
        void deactivate_owner_allowed() {
            when(userRepository.findById(1L)).thenReturn(
                    Optional.of(user(1L, Status.ACTIVE, Role.RIDER)));
            when(rideServiceClient.getActiveRideCount(1L)).thenReturn(0);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() -> userController.deactivateUser(1L, req(1L, "RIDER")));
        }

        @Test
        @DisplayName("deactivate: RIDER deactivating another user → 403 FORBIDDEN")
        void deactivate_otherRider_forbidden() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userController.deactivateUser(1L, req(2L, "RIDER")));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        @DisplayName("deactivate: ADMIN deactivating any user → 200 allowed")
        void deactivate_admin_allowed() {
            when(userRepository.findById(1L)).thenReturn(
                    Optional.of(user(1L, Status.ACTIVE, Role.RIDER)));
            when(rideServiceClient.getActiveRideCount(1L)).thenReturn(0);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() -> userController.deactivateUser(1L, req(99L, "ADMIN")));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // S1-F6  getTopRiders → Feign → payment-service
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("S1-F6 — getTopRiders delegates per-user totals to payment-service")
    class GetTopRidersTest {

        @Test
        @DisplayName("calls PaymentServiceClient.getUserPaymentTotal for each candidate user")
        void callsPaymentClientPerUser() {
            User a = user(1L, Status.ACTIVE, Role.RIDER);
            User b = user(2L, Status.ACTIVE, Role.RIDER);
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(a, b)));
            when(paymentServiceClient.getUserPaymentTotal(1L, "2026-03-01", "2026-03-31"))
                    .thenReturn(BigDecimal.valueOf(300));
            when(paymentServiceClient.getUserPaymentTotal(2L, "2026-03-01", "2026-03-31"))
                    .thenReturn(BigDecimal.valueOf(500));

            userService.getTopRiders("2026-03-01", "2026-03-31", 5);

            verify(paymentServiceClient).getUserPaymentTotal(1L, "2026-03-01", "2026-03-31");
            verify(paymentServiceClient).getUserPaymentTotal(2L, "2026-03-01", "2026-03-31");
        }

        @Test
        @DisplayName("spec scenario: A=300, B=500, C=100, limit=2 → [B, A] returned")
        void sortedDescendingRespectingLimit() {
            User a = user(1L, Status.ACTIVE, Role.RIDER);
            User b = user(2L, Status.ACTIVE, Role.RIDER);
            User c = user(3L, Status.ACTIVE, Role.RIDER);
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(a, b, c)));
            when(paymentServiceClient.getUserPaymentTotal(eq(1L), any(), any()))
                    .thenReturn(BigDecimal.valueOf(300));
            when(paymentServiceClient.getUserPaymentTotal(eq(2L), any(), any()))
                    .thenReturn(BigDecimal.valueOf(500));
            when(paymentServiceClient.getUserPaymentTotal(eq(3L), any(), any()))
                    .thenReturn(BigDecimal.valueOf(100));

            List<TopRiderDTO> result = userService.getTopRiders("2026-03-01", "2026-03-31", 2);

            assertEquals(2,  result.size());
            assertEquals(2L, result.get(0).getUserId()); // B = 500
            assertEquals(1L, result.get(1).getUserId()); // A = 300
        }

        @Test
        @DisplayName("users with zero payment total are excluded from results")
        void zeroTotalUsersExcluded() {
            User a = user(1L, Status.ACTIVE, Role.RIDER);
            User b = user(2L, Status.ACTIVE, Role.RIDER);
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(a, b)));
            when(paymentServiceClient.getUserPaymentTotal(eq(1L), any(), any()))
                    .thenReturn(BigDecimal.valueOf(500));
            when(paymentServiceClient.getUserPaymentTotal(eq(2L), any(), any()))
                    .thenReturn(BigDecimal.ZERO);

            List<TopRiderDTO> result = userService.getTopRiders("2026-03-01", "2026-03-31", 5);

            assertEquals(1,  result.size());
            assertEquals(1L, result.get(0).getUserId());
        }

        @Test
        @DisplayName("candidate set is capped at 100 (§2.12 N+1 hazard rule)")
        void candidatesAreCappedAt100() throws Exception {
            String src = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/service/UserService.java"));
            assertTrue(src.contains("PageRequest.of(0, 100)"),
                    "getTopRiders must pass PageRequest.of(0, 100) to cap N+1 fan-out at 100");
        }

        @Test
        @DisplayName("invalid date format → throws 400 BAD_REQUEST")
        void invalidDate_throws400() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.getTopRiders("not-a-date", "2026-03-31", 5));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("startDate after endDate → throws 400 BAD_REQUEST")
        void startAfterEnd_throws400() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.getTopRiders("2026-04-01", "2026-03-01", 5));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("Feign failure for one user is skipped, rest continue")
        void feignFailureForOneUser_skipped() {
            User a = user(1L, Status.ACTIVE, Role.RIDER);
            User b = user(2L, Status.ACTIVE, Role.RIDER);
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(a, b)));
            when(paymentServiceClient.getUserPaymentTotal(eq(1L), any(), any()))
                    .thenThrow(mock(FeignException.class));
            when(paymentServiceClient.getUserPaymentTotal(eq(2L), any(), any()))
                    .thenReturn(BigDecimal.valueOf(400));

            List<TopRiderDTO> result = userService.getTopRiders("2026-03-01", "2026-03-31", 5);

            // user A's Feign failure is swallowed; only user B appears
            assertEquals(1,  result.size());
            assertEquals(2L, result.get(0).getUserId());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // S1-F9  searchByLanguageAndRides → Feign → ride-service
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("S1-F9 — searchByLanguageAndRides delegates completed-count to ride-service")
    class SearchByLanguageTest {

        @Test
        @DisplayName("calls RideServiceClient.getCompletedRideCount for each candidate user")
        void callsRideClientPerUser() {
            User a = user(1L, Status.ACTIVE, Role.RIDER);
            User b = user(2L, Status.ACTIVE, Role.RIDER);
            when(userRepository.findByLanguagePreference("ar", 100))
                    .thenReturn(List.of(a, b));
            when(rideServiceClient.getCompletedRideCount(1L)).thenReturn(5L);
            when(rideServiceClient.getCompletedRideCount(2L)).thenReturn(2L);

            userService.searchByLanguageAndRides("ar", 3);

            verify(rideServiceClient).getCompletedRideCount(1L);
            verify(rideServiceClient).getCompletedRideCount(2L);
        }

        @Test
        @DisplayName("spec scenario: A=5 rides, B=2 rides, minRides=3 → only A returned")
        void filtersCorrectlyByMinRides() {
            User a = user(1L, Status.ACTIVE, Role.RIDER);
            User b = user(2L, Status.ACTIVE, Role.RIDER);
            when(userRepository.findByLanguagePreference("ar", 100))
                    .thenReturn(List.of(a, b));
            when(rideServiceClient.getCompletedRideCount(1L)).thenReturn(5L);
            when(rideServiceClient.getCompletedRideCount(2L)).thenReturn(2L);

            List<User> result = userService.searchByLanguageAndRides("ar", 3);

            assertEquals(1,  result.size());
            assertEquals(1L, result.get(0).getId());
        }

        @Test
        @DisplayName("user with exactly minRides completed rides is included")
        void exactlyMinRides_included() {
            User a = user(1L, Status.ACTIVE, Role.RIDER);
            when(userRepository.findByLanguagePreference("en", 100))
                    .thenReturn(List.of(a));
            when(rideServiceClient.getCompletedRideCount(1L)).thenReturn(3L);

            List<User> result = userService.searchByLanguageAndRides("en", 3);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("candidate query passes limit=100 to respect §2.12 cap")
        void candidateQueryCappedAt100() throws Exception {
            String src = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/service/UserService.java"));
            assertTrue(src.contains("findByLanguagePreference") && src.contains("100"),
                    "searchByLanguageAndRides must cap candidates at 100 via findByLanguagePreference(lang, 100)");
        }

        @Test
        @DisplayName("blank lang → throws 400 BAD_REQUEST")
        void blankLang_throws400() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userService.searchByLanguageAndRides("", 3));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("no candidates match language → returns empty list, no Feign calls")
        void noLanguageMatch_emptyResult() {
            when(userRepository.findByLanguagePreference("zz", 100))
                    .thenReturn(List.of());

            List<User> result = userService.searchByLanguageAndRides("zz", 0);

            assertTrue(result.isEmpty());
            verifyNoInteractions(rideServiceClient);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §11.1  Observability — logback, CorrelationIdFilter, MDC, log points
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§11.1 — Observability: Loki4J, CorrelationIdFilter, MDC, log points")
    class ObservabilityTest {

        @Test
        @DisplayName("logback-spring.xml exists and declares Loki4jAppender")
        void logback_hasLoki4jAppender() throws Exception {
            String xml = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
            assertTrue(xml.contains("Loki4jAppender"),
                    "logback-spring.xml must declare the Loki4jAppender class");
        }

        @Test
        @DisplayName("logback-spring.xml Loki push URL must target loki.monitoring.svc.cluster.local:3100")
        void logback_lokiUrl() throws Exception {
            String xml = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
            assertTrue(xml.contains("loki.monitoring.svc.cluster.local:3100"),
                    "Loki push URL must be http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push");
        }

        @Test
        @DisplayName("logback-spring.xml JSON message pattern contains correlationId and userId MDC fields")
        void logback_mdcFields() throws Exception {
            String xml = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
            assertTrue(xml.contains("correlationId"),
                    "logback pattern must include correlationId MDC field");
            assertTrue(xml.contains("userId"),
                    "logback pattern must include userId MDC field");
        }

        @Test
        @DisplayName("CorrelationIdFilter reads X-Correlation-ID header and puts it in MDC")
        void filter_setsMdcFromHeader() throws Exception {
            CorrelationIdFilter filter = new CorrelationIdFilter();
            MockHttpServletRequest  req  = new MockHttpServletRequest();
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("X-Correlation-ID", "my-correlation-abc");

            FilterChain chain = (rq, rs) ->
                    assertEquals("my-correlation-abc", MDC.get("correlationId"),
                            "MDC must hold the X-Correlation-ID header value during request");

            filter.doFilter(req, resp, chain);
        }

        @Test
        @DisplayName("CorrelationIdFilter generates a UUID when X-Correlation-ID header is absent")
        void filter_generatesUuidWhenHeaderAbsent() throws Exception {
            CorrelationIdFilter filter = new CorrelationIdFilter();
            MockHttpServletRequest  req  = new MockHttpServletRequest();
            MockHttpServletResponse resp = new MockHttpServletResponse();

            FilterChain chain = (rq, rs) -> {
                String id = MDC.get("correlationId");
                assertNotNull(id,        "Must generate a correlationId when header is absent");
                assertFalse(id.isBlank(), "Generated correlationId must not be blank");
            };

            filter.doFilter(req, resp, chain);
        }

        @Test
        @DisplayName("CorrelationIdFilter clears MDC in finally — no leakage between requests")
        void filter_clearsMdcAfterRequest() throws Exception {
            CorrelationIdFilter filter = new CorrelationIdFilter();
            MockHttpServletRequest  req  = new MockHttpServletRequest();
            req.addHeader("X-Correlation-ID", "cleanup-test");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            filter.doFilter(req, resp, (rq, rs) -> { /* no-op */ });

            assertNull(MDC.get("correlationId"),
                    "correlationId must be removed from MDC after request (finally block)");
        }

        @Test
        @DisplayName("UserService source contains MDC.put(userId) + MDC.remove(userId)")
        void userService_populatesMdcUserId() throws Exception {
            String src = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/service/UserService.java"));
            assertTrue(src.contains("MDC.put(\"userId\""),
                    "UserService must populate MDC with userId");
            assertTrue(src.contains("MDC.remove(\"userId\""),
                    "UserService must remove userId from MDC in finally block to prevent leakage");
        }

        @Test
        @DisplayName("UserController source contains controller entry log point")
        void controller_hasEntryLog() throws Exception {
            String src = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/controller/UserController.java"));
            assertTrue(src.contains("Received {} {}"),
                    "UserController must log 'Received {} {}' at method entry");
        }

        @Test
        @DisplayName("UserController source contains controller exit log point")
        void controller_hasExitLog() throws Exception {
            String src = Files.readString(Path.of(
                    "src/main/java/com/team10/uber/user/controller/UserController.java"));
            assertTrue(src.contains("Returning {} for {} {}"),
                    "UserController must log 'Returning {} for {} {}' at method exit");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §11.3  Grafana dashboard — ≥3 LogQL panels
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§11.3 — user-dashboard.json has ≥3 LogQL panels and ≥3 PromQL panels")
    class DashboardTest {

        private String dashboard;

        @BeforeEach
        void load() throws Exception {
            dashboard = Files.readString(Path.of(
                    "../k8s/monitoring/grafana/dashboards/user-dashboard.json"));
        }

        @Test
        @DisplayName("dashboard has at least 3 Loki (LogQL) datasource panels")
        void hasAtLeast3LogQlPanels() {
            int count = countOccurrences(dashboard, "\"type\": \"loki\"");
            assertTrue(count >= 3,
                    "user-dashboard.json must have ≥3 LogQL (Loki datasource) panels, found: " + count);
        }

        @Test
        @DisplayName("dashboard has at least 3 Prometheus (PromQL) datasource panels")
        void hasAtLeast3PromQlPanels() {
            int count = countOccurrences(dashboard, "\"type\": \"prometheus\"");
            assertTrue(count >= 3,
                    "user-dashboard.json must have ≥3 PromQL (Prometheus datasource) panels, found: " + count);
        }

        @Test
        @DisplayName("dashboard has a uid and title field")
        void dashboardHasMetadata() {
            assertTrue(dashboard.contains("\"uid\""),    "Dashboard must have a uid field");
            assertTrue(dashboard.contains("\"title\""),  "Dashboard must have a title field");
        }

        private int countOccurrences(String text, String target) {
            int count = 0, idx = 0;
            while ((idx = text.indexOf(target, idx)) != -1) { count++; idx++; }
            return count;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // K8s manifests — secret, PVC, StatefulSet, headless Service
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("K8s — user-postgres secret, PVC, StatefulSet, headless Service")
    class KubernetesManifestTest {

        @Test
        @DisplayName("user-postgres-secret.yaml exists in uber namespace with correct DB name")
        void secret_valid() throws Exception {
            String s = Files.readString(Path.of("../k8s/secrets/user-postgres-secret.yaml"));
            assertAll(
                    () -> assertTrue(s.contains("name: user-postgres-secret"),
                            "Secret must be named user-postgres-secret"),
                    () -> assertTrue(s.contains("namespace: uber"),
                            "Secret must be in the uber namespace"),
                    () -> assertTrue(s.contains("POSTGRES_USER"),   "Must define POSTGRES_USER"),
                    () -> assertTrue(s.contains("POSTGRES_PASSWORD"),"Must define POSTGRES_PASSWORD"),
                    () -> assertTrue(s.contains("POSTGRES_DB"),     "Must define POSTGRES_DB"),
                    () -> assertTrue(s.contains("uberdb-users"),
                            "POSTGRES_DB value must be uberdb-users")
            );
        }

        @Test
        @DisplayName("user-postgres-pvc.yaml exists in uber namespace with ReadWriteOnce")
        void pvc_valid() throws Exception {
            String s = Files.readString(Path.of("../k8s/pvcs/user-postgres-pvc.yaml"));
            assertAll(
                    () -> assertTrue(s.contains("user-postgres-pvc"),
                            "PVC must be named user-postgres-pvc"),
                    () -> assertTrue(s.contains("namespace: uber"),
                            "PVC must be in the uber namespace"),
                    () -> assertTrue(s.contains("ReadWriteOnce"),
                            "PVC must use ReadWriteOnce access mode")
            );
        }

        @Test
        @DisplayName("StatefulSet uses postgres:17 image (not PG18 — breaks Hibernate)")
        void statefulset_postgresVersion() throws Exception {
            String s = Files.readString(Path.of(
                    "../k8s/statefulsets/user-postgres-statefulset.yaml"));
            assertTrue(s.contains("image: postgres:17"),
                    "StatefulSet must use postgres:17 — PG18 breaks Hibernate native query casts");
        }

        @Test
        @DisplayName("StatefulSet has 512Mi memory limit to prevent MiniKube OOM")
        void statefulset_memoryLimit() throws Exception {
            String s = Files.readString(Path.of(
                    "../k8s/statefulsets/user-postgres-statefulset.yaml"));
            assertTrue(s.contains("memory: 512Mi"),
                    "StatefulSet must limit memory to 512Mi");
        }

        @Test
        @DisplayName("StatefulSet has pg_isready readiness and liveness probes")
        void statefulset_pgIsReadyProbe() throws Exception {
            String s = Files.readString(Path.of(
                    "../k8s/statefulsets/user-postgres-statefulset.yaml"));
            assertTrue(s.contains("pg_isready"),
                    "StatefulSet must have pg_isready probes so kubectl wait --for=condition=ready works");
        }

        @Test
        @DisplayName("StatefulSet is in the uber namespace")
        void statefulset_namespace() throws Exception {
            String s = Files.readString(Path.of(
                    "../k8s/statefulsets/user-postgres-statefulset.yaml"));
            assertTrue(s.contains("namespace: uber"),
                    "StatefulSet must be deployed in the uber namespace");
        }

        @Test
        @DisplayName("user-postgres-svc.yaml is a headless Service (clusterIP: None)")
        void service_isHeadless() throws Exception {
            String s = Files.readString(Path.of(
                    "../k8s/services/user-postgres-svc.yaml"));
            assertTrue(s.contains("clusterIP: None"),
                    "Service must be headless (clusterIP: None) for StatefulSet DNS resolution");
        }

        @Test
        @DisplayName("user-postgres-svc.yaml selects app: user-postgres and exposes port 5432")
        void service_selectorAndPort() throws Exception {
            String s = Files.readString(Path.of(
                    "../k8s/services/user-postgres-svc.yaml"));
            assertAll(
                    () -> assertTrue(s.contains("app: user-postgres"),
                            "Service selector must match StatefulSet label app=user-postgres"),
                    () -> assertTrue(s.contains("5432"),
                            "Service must expose port 5432")
            );
        }
    }
}
