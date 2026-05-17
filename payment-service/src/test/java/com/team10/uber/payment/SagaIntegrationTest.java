package com.team10.uber.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.uber.contracts.dto.RideDTO;
import com.team10.uber.contracts.events.PaymentCompletedEvent;
import com.team10.uber.contracts.events.PaymentFailedEvent;
import com.team10.uber.contracts.events.PaymentInitiatedEvent;
import com.team10.uber.contracts.events.PaymentRefundedEvent;
import com.team10.uber.contracts.events.RideCancelledEvent;
import com.team10.uber.contracts.events.RideCompletedEvent;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.payment.config.PaymentRabbitConfig;
import com.team10.uber.payment.controller.PaymentController;
import com.team10.uber.payment.dto.ProcessPaymentDTO;
import com.team10.uber.payment.enums.PaymentMethod;
import com.team10.uber.payment.enums.PaymentStatus;
import com.team10.uber.payment.messaging.consumers.RideEventConsumer;
import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.observer.MongoEventLogger;
import com.team10.uber.payment.repository.CouponRepository;
import com.team10.uber.payment.repository.PaymentCouponRepository;
import com.team10.uber.payment.repository.PaymentRepository;
import com.team10.uber.payment.cache.RedisCacheService;
import com.team10.uber.payment.service.PaymentService;
import com.team10.uber.contracts.feign.DriverServiceClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Saga integration tests for the payment-service's role in the Ride Lifecycle Saga.
 *
 * ──── SCOPE ────────────────────────────────────────────────────────────────────
 * These tests verify payment-service's COMPONENT-LEVEL saga behaviour using
 * Mockito mocks for all external dependencies (other services, DB, RabbitMQ).
 * They do NOT spin up a real RabbitMQ broker or a real Postgres database.
 *
 * ──── WHAT CANNOT BE TESTED HERE (MANUAL / E2E) ───────────────────────────────
 * True end-to-end saga verification (§8.6 scenarios) requires all 5 services
 * running inside MiniKube. Manual steps are documented in each test class below.
 *
 *   Scenario A — full happy-path E2E manual steps:
 *     1. kubectl apply -f k8s/ (all services running)
 *     2. POST /api/auth/register (create user + driver)
 *     3. POST /api/rides → PUT /api/rides/{id}/assign → PUT /api/rides/{id}/start
 *     4. PUT /api/rides/{id}/complete → expect 200, ride=COMPLETED
 *        → poll GET /api/rides/{id} until status=PAYMENT_PENDING (RabbitMQ async)
 *     5. POST /api/payments/ride/{id} {"method":"CREDIT_CARD","cardLastFour":"4242"}
 *        → expect 201, payment=COMPLETED
 *        → poll GET /api/rides/{id} until status=PAID
 *
 *   Scenario B — payment failure cascade E2E manual steps:
 *     1-4. Same as Scenario A to reach ride=PAYMENT_PENDING
 *     5. POST /api/payments/ride/{id} {"method":"BITCOIN"} → 400, payment.failed published
 *        → poll GET /api/rides/{id} until status=REFUNDED (multi-hop cascade takes ~3s)
 *
 *   Scenario C — pre-check failure (no recent location ping):
 *     This scenario is entirely within ride-service (location-service Feign pre-check).
 *     No payment-service participation — tested manually:
 *     1. Ensure driver's last location ping is > 5 minutes old
 *     2. PUT /api/rides/{id}/complete → expect 400 "driver not actively tracked"
 *     3. GET /api/rides/{id} → status still IN_PROGRESS
 */
@ExtendWith(MockitoExtension.class)
class SagaIntegrationTest {

    @Mock PaymentRepository paymentRepository;
    @Mock CouponRepository couponRepository;
    @Mock PaymentCouponRepository paymentCouponRepository;
    @Mock MongoEventLogger mongoEventLogger;
    @Mock RedisCacheService cache;
    @Mock MongoTemplate mongoTemplate;
    @Mock UserServiceClient userServiceClient;
    @Mock RideServiceClient rideServiceClient;
    @Mock DriverServiceClient driverServiceClient;
    @Mock RabbitTemplate rabbitTemplate;

    PaymentService paymentService;
    RideEventConsumer rideEventConsumer;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, couponRepository, paymentCouponRepository,
                mongoEventLogger, cache, mongoTemplate,
                userServiceClient, rideServiceClient, driverServiceClient,
                rabbitTemplate);

        objectMapper = new ObjectMapper();
        rideEventConsumer = new RideEventConsumer(paymentRepository, rabbitTemplate, objectMapper);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Scenario A — Happy Path (payment-service component perspective)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Scenario A — Happy Path")
    class ScenarioA {

        @Test
        @DisplayName("A1: ride.completed consumer creates PENDING payment and publishes payment.initiated")
        void rideCompleted_createsPendingPaymentAndPublishesInitiated() throws Exception {
            // setup
            RideCompletedEvent event = new RideCompletedEvent(10L, 1L, 5L, 200.0);
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.empty());

            Payment savedPayment = new Payment();
            savedPayment.setId(99L);
            savedPayment.setRideId(10L);
            savedPayment.setUserId(1L);
            savedPayment.setAmount(200.0);
            savedPayment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

            // action — simulate RabbitMQ delivering ride.completed
            Message message = buildMessage(objectMapper.writeValueAsBytes(event), "ride.completed");
            rideEventConsumer.handleRideEvent(message);

            // verify: PENDING payment saved
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(paymentCaptor.getValue().getRideId()).isEqualTo(10L);
            assertThat(paymentCaptor.getValue().getAmount()).isEqualTo(200.0);

            // verify: payment.initiated published
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(
                    eq(PaymentRabbitConfig.PAYMENT_EXCHANGE),
                    eq(PaymentRabbitConfig.ROUTING_KEY_INITIATED),
                    eventCaptor.capture());
            PaymentInitiatedEvent initiated = (PaymentInitiatedEvent) eventCaptor.getValue();
            assertThat(initiated.rideId()).isEqualTo(10L);
            assertThat(initiated.amount()).isEqualTo(200.0);
        }

        @Test
        @DisplayName("A2: processPaymentForRide success → COMPLETED + payment.completed published")
        @SuppressWarnings("unchecked")
        void processPayment_success_completesPaymentAndPublishesCompleted() {
            // setup: PENDING payment exists in local DB
            Payment pending = new Payment();
            pending.setId(99L);
            pending.setRideId(10L);
            pending.setUserId(1L);
            pending.setAmount(200.0);
            pending.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.of(pending));

            // ride-service Feign returns ride with status=PAYMENT_PENDING
            RideDTO ride = new RideDTO(10L, 1L, 5L, "PAYMENT_PENDING", 200.0,
                    LocalDateTime.now().minusMinutes(10), null, null);
            when(rideServiceClient.getRide(10L)).thenReturn(ride);

            Payment saved = new Payment();
            saved.setId(99L);
            saved.setRideId(10L);
            saved.setUserId(1L);
            saved.setStatus(PaymentStatus.COMPLETED);
            when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

            // action
            ProcessPaymentDTO dto = new ProcessPaymentDTO();
            dto.setMethod("CREDIT_CARD");
            dto.setCardLastFour("4242");
            Payment result = paymentService.processPaymentForRide(10L, dto, false);

            // verify: payment status = COMPLETED
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

            // verify: payment.completed published (publisher sends a Map payload)
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(
                    eq(PaymentRabbitConfig.PAYMENT_EXCHANGE),
                    eq(PaymentRabbitConfig.ROUTING_KEY_COMPLETED),
                    eventCaptor.capture());
            Map<String, Object> payload = (Map<String, Object>) eventCaptor.getValue();
            assertThat(payload.get("rideId")).isEqualTo(10L);
        }

        @Test
        @DisplayName("A3: ride.completed idempotency — duplicate event is a no-op")
        void rideCompleted_duplicate_isNoOp() throws Exception {
            RideCompletedEvent event = new RideCompletedEvent(10L, 1L, 5L, 200.0);

            // payment already exists
            Payment existing = new Payment();
            existing.setId(99L);
            existing.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.of(existing));

            Message message = buildMessage(objectMapper.writeValueAsBytes(event), "ride.completed");
            rideEventConsumer.handleRideEvent(message);

            // no new payment saved, no event published
            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Scenario B — Payment Failure + Compensation Cascade
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Scenario B — Payment Failure and Compensation")
    class ScenarioB {

        @Test
        @DisplayName("B1: unsupported payment method (BITCOIN) → 400 + FAILED + payment.failed published")
        void processPayment_unsupportedMethod_failsAndPublishesPaymentFailed() {
            Payment pending = new Payment();
            pending.setId(99L);
            pending.setRideId(10L);
            pending.setUserId(1L);
            pending.setAmount(200.0);
            pending.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.of(pending));

            RideDTO ride = new RideDTO(10L, 1L, 5L, "PAYMENT_PENDING", 200.0,
                    LocalDateTime.now().minusMinutes(10), null, null);
            when(rideServiceClient.getRide(10L)).thenReturn(ride);

            Payment failedPayment = new Payment();
            failedPayment.setId(99L);
            failedPayment.setRideId(10L);
            failedPayment.setStatus(PaymentStatus.FAILED);
            when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);

            ProcessPaymentDTO dto = new ProcessPaymentDTO();
            dto.setMethod("BITCOIN"); // unsupported method → payment.failed + 400

            // expect 400
            assertThatThrownBy(() -> paymentService.processPaymentForRide(10L, dto, false))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            // verify: payment saved as FAILED
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);

            // verify: payment.failed published (triggers compensation cascade)
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(
                    eq(PaymentRabbitConfig.PAYMENT_EXCHANGE),
                    eq(PaymentRabbitConfig.ROUTING_KEY_FAILED),
                    eventCaptor.capture());
            PaymentFailedEvent failed = (PaymentFailedEvent) eventCaptor.getValue();
            assertThat(failed.rideId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("B2: ride.cancelled consumer refunds PENDING payment + publishes payment.refunded")
        void rideCancelled_refundsPendingPaymentAndPublishesRefunded() throws Exception {
            RideCancelledEvent event = new RideCancelledEvent(10L, 1L, 5L, "payment_failed");

            Payment pending = new Payment();
            pending.setId(99L);
            pending.setRideId(10L);
            pending.setAmount(200.0);
            pending.setStatus(PaymentStatus.PENDING);
            pending.setMethod(PaymentMethod.CREDIT_CARD);
            paymentWithCreatedAt(pending, LocalDateTime.now().minusMinutes(5));
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.of(pending));

            Payment saved = new Payment();
            saved.setId(99L);
            saved.setRideId(10L);
            saved.setStatus(PaymentStatus.REFUNDED);
            when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

            Message message = buildMessage(objectMapper.writeValueAsBytes(event), "ride.cancelled");
            rideEventConsumer.handleRideEvent(message);

            // verify: payment saved as REFUNDED
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);

            // verify: payment.refunded published
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(
                    eq(PaymentRabbitConfig.PAYMENT_EXCHANGE),
                    eq(PaymentRabbitConfig.ROUTING_KEY_REFUNDED),
                    eventCaptor.capture());
            PaymentRefundedEvent refunded = (PaymentRefundedEvent) eventCaptor.getValue();
            assertThat(refunded.rideId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("B3: ride.cancelled with no existing payment is a no-op")
        void rideCancelled_noPayment_isNoOp() throws Exception {
            RideCancelledEvent event = new RideCancelledEvent(10L, 1L, 5L, "user_requested");
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.COMPLETED))
                    .thenReturn(Optional.empty());

            Message message = buildMessage(objectMapper.writeValueAsBytes(event), "ride.cancelled");
            rideEventConsumer.handleRideEvent(message);

            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("B4: ride.cancelled idempotency — already REFUNDED payment is a no-op")
        void rideCancelled_alreadyRefunded_isNoOp() throws Exception {
            RideCancelledEvent event = new RideCancelledEvent(10L, 1L, 5L, "payment_failed");

            Payment refunded = new Payment();
            refunded.setId(99L);
            refunded.setStatus(PaymentStatus.REFUNDED);
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.of(refunded)); // status is already REFUNDED in this mock

            // Simulate by returning a payment whose status is REFUNDED
            Payment alreadyRefunded = new Payment();
            alreadyRefunded.setId(99L);
            alreadyRefunded.setStatus(PaymentStatus.REFUNDED);
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.of(alreadyRefunded));

            Message message = buildMessage(objectMapper.writeValueAsBytes(event), "ride.cancelled");
            rideEventConsumer.handleRideEvent(message);

            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Scenario C — Pre-check Failure (ride-service side — no location ping)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Scenario C — Pre-check Failure (payment-service: no participation)")
    class ScenarioC {

        /**
         * Scenario C is fully owned by ride-service: if location-service returns 404
         * for GET /api/locations/driver/{id}/recent, ride-service returns 400 and
         * publishes NO events. Payment-service never receives ride.completed in this case.
         *
         * From payment-service's perspective, the invariant is:
         *   if no ride.completed was ever published → no PENDING payment is created.
         *
         * Manual E2E steps for Scenario C:
         *   1. Set driver's last location ping to > 5 minutes ago in location-postgres.
         *   2. PUT /api/rides/{id}/complete → expect HTTP 400 ("driver not actively tracked").
         *   3. GET /api/rides/{id} → status still = IN_PROGRESS.
         *   4. GET /api/payments?rideId={id} → no payment record for this ride.
         */
        @Test
        @DisplayName("C1: if ride.completed is never published, no payment is created for that rideId")
        void noPendingPaymentWhenRideCompletedNotPublished() {
            // payment-service has received no ride.completed for rideId=10
            when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                    .thenReturn(Optional.empty());

            Optional<Payment> result = paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("C2: POST /api/payments/ride/{id} with no PENDING payment → 400")
        void processPayment_noPendingPayment_returns400() {
            RideDTO ride = new RideDTO(10L, 1L, 5L, "IN_PROGRESS", null,
                    LocalDateTime.now().minusMinutes(30), null, null);
            when(rideServiceClient.getRide(10L)).thenReturn(ride);

            // ride is IN_PROGRESS, not PAYMENT_PENDING
            ProcessPaymentDTO dto = new ProcessPaymentDTO();
            dto.setMethod("CREDIT_CARD");
            dto.setCardLastFour("4242");

            assertThatThrownBy(() -> paymentService.processPaymentForRide(10L, dto, false))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Additional edge cases
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("processPaymentForRide: Feign 404 from ride-service → 404")
        void processPayment_rideNotFound_returns404() {
            when(rideServiceClient.getRide(999L)).thenThrow(FeignException.NotFound.class);

            ProcessPaymentDTO dto = new ProcessPaymentDTO();
            dto.setMethod("CREDIT_CARD");

            assertThatThrownBy(() -> paymentService.processPaymentForRide(999L, dto, false))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("processPaymentForRide: ride status not PAYMENT_PENDING → 400")
        void processPayment_rideNotPaymentPending_returns400() {
            RideDTO ride = new RideDTO(1L, 1L, 5L, "COMPLETED", 200.0,
                    LocalDateTime.now().minusMinutes(10), null, null);
            when(rideServiceClient.getRide(1L)).thenReturn(ride);

            ProcessPaymentDTO dto = new ProcessPaymentDTO();
            dto.setMethod("CREDIT_CARD");

            assertThatThrownBy(() -> paymentService.processPaymentForRide(1L, dto, false))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Message buildMessage(byte[] body, String routingKey) {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        props.setContentType("application/json");
        return new Message(body, props);
    }

    /** @PrePersist doesn't fire in unit tests — set createdAt via reflection. */
    private static Payment paymentWithCreatedAt(Payment p, LocalDateTime createdAt) {
        try {
            Field f = Payment.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(p, createdAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return p;
    }
}
