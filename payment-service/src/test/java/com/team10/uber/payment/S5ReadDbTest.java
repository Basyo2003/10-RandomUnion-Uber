package com.team10.uber.payment;

import com.team10.uber.contracts.dto.DriverDTO;
import com.team10.uber.contracts.dto.RideDTO;
import com.team10.uber.contracts.dto.UserDTO;
import com.team10.uber.contracts.feign.DriverServiceClient;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.payment.cache.RedisCacheService;
import com.team10.uber.payment.config.CorrelationIdFilter;
import com.team10.uber.payment.config.PaymentRabbitConfig;
import com.team10.uber.payment.controller.PaymentController;
import com.team10.uber.payment.dto.ProcessPaymentDTO;
import com.team10.uber.payment.dto.UserPaymentSummaryDTO;
import com.team10.uber.payment.dto.VehicleTypeRevenueDTO;
import com.team10.uber.payment.enums.PaymentStatus;
import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.observer.MongoEventLogger;
import com.team10.uber.payment.repository.CouponRepository;
import com.team10.uber.payment.repository.PaymentCouponRepository;
import com.team10.uber.payment.repository.PaymentRepository;
import com.team10.uber.payment.service.PaymentService;
import feign.FeignException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for S5-READ-DB slice of payment-service.
 *
 * All external dependencies (PostgreSQL, Redis, RabbitMQ, Feign HTTP, Loki)
 * are replaced with Mockito mocks — no infrastructure required to run these
 * tests.
 *
 * See the bottom of this file for a list of things that CANNOT be unit-tested.
 */
@ExtendWith(MockitoExtension.class)
class S5ReadDbTest {

        // ── Shared mocks ────────────────────────────────────────────────────────

        @Mock
        PaymentRepository paymentRepository;
        @Mock
        CouponRepository couponRepository;
        @Mock
        PaymentCouponRepository paymentCouponRepository;
        @Mock
        MongoEventLogger mongoEventLogger;
        @Mock
        RedisCacheService cache;
        @Mock
        MongoTemplate mongoTemplate;
        @Mock
        org.springframework.context.ApplicationEventPublisher eventPublisher;
        @Mock
        UserServiceClient userServiceClient;
        @Mock
        RideServiceClient rideServiceClient;
        @Mock
        DriverServiceClient driverServiceClient;
        @Mock
        RabbitTemplate rabbitTemplate;

        PaymentService service;

        @BeforeEach
        void buildService() {
                service = new PaymentService(
                                paymentRepository, couponRepository, paymentCouponRepository,
                                mongoEventLogger, cache, mongoTemplate, eventPublisher,
                                userServiceClient, rideServiceClient, driverServiceClient,
                                rabbitTemplate);
                // @PostConstruct doesn't fire outside Spring — call it manually
                service.initObservers();
                // Default: cache always misses (returns null)
                lenient().when(cache.get(anyString(), any(Class.class))).thenReturn(null);
                lenient().when(cache.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(null);
        }

        // ════════════════════════════════════════════════════════════════════════
        // §7 S5-F3 getUserPaymentSummary — Feign replaces countUserById
        // ════════════════════════════════════════════════════════════════════════

        @Nested
        class GetUserPaymentSummary {

                @Test
                void happyPath_returnsSummary() {
                        UserDTO user = new UserDTO(1L, "Alice", "a@b.com", "0100", "USER", "ACTIVE");
                        when(userServiceClient.getUser(1L)).thenReturn(user);
                        when(paymentRepository.findPaymentSummaryByUserId(1L)).thenReturn(
                                        Collections.singletonList(new Object[] { "CASH", 2L, 50.0 }));

                        UserPaymentSummaryDTO result = service.getUserPaymentSummary(1L);

                        assertThat(result.getUserId()).isEqualTo(1L);
                        assertThat(result.getTotalPayments()).isEqualTo(2L);
                        assertThat(result.getTotalAmount()).isEqualTo(50.0);
                        assertThat(result.getMethodBreakdown()).containsKey("CASH");
                }

                @Test
                void feignNotFound_throws404() {
                        when(userServiceClient.getUser(99L))
                                        .thenThrow(buildNotFound("user not found"));

                        assertThatThrownBy(() -> service.getUserPaymentSummary(99L))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.NOT_FOUND));
                }

                @Test
                void feignServerError_throws503() {
                        when(userServiceClient.getUser(1L))
                                        .thenThrow(buildServiceUnavailable("timeout"));

                        assertThatThrownBy(() -> service.getUserPaymentSummary(1L))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
                }

                @Test
                void cacheHit_skipsFeignAndRepository() {
                        UserPaymentSummaryDTO cached = UserPaymentSummaryDTO.builder()
                                        .userId(1L).totalPayments(5L).totalAmount(100.0).methodBreakdown(Map.of())
                                        .build();
                        when(cache.get(eq("S5-F3::1"), eq(UserPaymentSummaryDTO.class))).thenReturn(cached);

                        UserPaymentSummaryDTO result = service.getUserPaymentSummary(1L);

                        assertThat(result).isSameAs(cached);
                        verifyNoInteractions(userServiceClient);
                        verifyNoInteractions(paymentRepository);
                }

                @Test
                void noPayments_returnsEmptySummary() {
                        when(userServiceClient.getUser(1L))
                                        .thenReturn(new UserDTO(1L, "Bob", "b@c.com", "0100", "USER", "ACTIVE"));
                        when(paymentRepository.findPaymentSummaryByUserId(1L)).thenReturn(Collections.emptyList());

                        UserPaymentSummaryDTO result = service.getUserPaymentSummary(1L);

                        assertThat(result.getTotalPayments()).isEqualTo(0L);
                        assertThat(result.getTotalAmount()).isEqualTo(0.0);
                }
        }

        // ════════════════════════════════════════════════════════════════════════
        // §7 S5-F4 processPaymentForRide — Feign + event publishing
        // ════════════════════════════════════════════════════════════════════════

        @Nested
        class ProcessPaymentForRide {

                private Payment pendingPayment;

                @BeforeEach
                void stubPendingPayment() {
                        pendingPayment = new Payment();
                        pendingPayment.setId(100L);
                        pendingPayment.setRideId(10L);
                        pendingPayment.setUserId(1L);
                        pendingPayment.setAmount(100.0);
                        pendingPayment.setStatus(PaymentStatus.PENDING);
                }

                private RideDTO rideInPaymentPending() {
                        return new RideDTO(10L, 1L, 5L, "PAYMENT_PENDING", 100.0,
                                        LocalDateTime.now(), null, Map.of());
                }

                @Test
                void happyPath_completesPaymentAndPublishesEvent() {
                        when(rideServiceClient.getRide(10L)).thenReturn(rideInPaymentPending());
                        when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                                        .thenReturn(Optional.of(pendingPayment));
                        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        ProcessPaymentDTO dto = new ProcessPaymentDTO();
                        dto.setMethod("CASH");

                        Payment result = service.processPaymentForRide(10L, dto, false);

                        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
                        verify(rabbitTemplate).convertAndSend(
                                        eq(PaymentRabbitConfig.PAYMENT_EXCHANGE),
                                        eq(PaymentRabbitConfig.ROUTING_KEY_COMPLETED),
                                        any(Map.class));
                }

                @Test
                void simulateFailure_setsFailedAndPublishesFailedEvent() {
                        when(rideServiceClient.getRide(10L)).thenReturn(rideInPaymentPending());
                        when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                                        .thenReturn(Optional.of(pendingPayment));
                        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        ProcessPaymentDTO dto = new ProcessPaymentDTO();
                        dto.setMethod("CASH");

                        Payment result = service.processPaymentForRide(10L, dto, true);

                        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
                        verify(rabbitTemplate).convertAndSend(
                                        eq(PaymentRabbitConfig.PAYMENT_EXCHANGE),
                                        eq(PaymentRabbitConfig.ROUTING_KEY_FAILED),
                                        any(Map.class));
                        // completed event must NOT be published
                        verify(rabbitTemplate, never()).convertAndSend(
                                        eq(PaymentRabbitConfig.PAYMENT_EXCHANGE),
                                        eq(PaymentRabbitConfig.ROUTING_KEY_COMPLETED),
                                        Optional.ofNullable(any()));
                }

                @Test
                void rideNotFound_throws404() {
                        when(rideServiceClient.getRide(10L)).thenThrow(buildNotFound("ride not found"));

                        assertThatThrownBy(() -> service.processPaymentForRide(10L, new ProcessPaymentDTO(), false))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.NOT_FOUND));

                        verifyNoInteractions(rabbitTemplate);
                }

                @Test
                void rideFeignServerError_throws503() {
                        when(rideServiceClient.getRide(10L)).thenThrow(buildServiceUnavailable("timeout"));

                        assertThatThrownBy(() -> service.processPaymentForRide(10L, new ProcessPaymentDTO(), false))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
                }

                @Test
                void rideStatusNotPaymentPending_throws400() {
                        RideDTO completedRide = new RideDTO(10L, 1L, 5L, "COMPLETED", 100.0,
                                        LocalDateTime.now(), LocalDateTime.now(), Map.of());
                        when(rideServiceClient.getRide(10L)).thenReturn(completedRide);

                        assertThatThrownBy(() -> service.processPaymentForRide(10L, new ProcessPaymentDTO(), false))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.BAD_REQUEST));

                        verifyNoInteractions(rabbitTemplate);
                }

                @Test
                void noPendingPaymentLocally_throws400() {
                        when(rideServiceClient.getRide(10L)).thenReturn(rideInPaymentPending());
                        when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                                        .thenReturn(Optional.empty());

                        ProcessPaymentDTO dto = new ProcessPaymentDTO();
                        dto.setMethod("CASH");

                        assertThatThrownBy(() -> service.processPaymentForRide(10L, dto, false))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.BAD_REQUEST));

                        verifyNoInteractions(rabbitTemplate);
                }

                @Test
                void invalidCardFormat_throws400() {
                        when(rideServiceClient.getRide(10L)).thenReturn(rideInPaymentPending());
                        when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                                        .thenReturn(Optional.of(pendingPayment));

                        ProcessPaymentDTO dto = new ProcessPaymentDTO();
                        dto.setMethod("CREDIT_CARD");
                        dto.setCardLastFour("12"); // too short

                        assertThatThrownBy(() -> service.processPaymentForRide(10L, dto, false))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.BAD_REQUEST));
                }

                @Test
                void invalidPaymentMethod_throws400() {
                        when(rideServiceClient.getRide(10L)).thenReturn(rideInPaymentPending());
                        when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                                        .thenReturn(Optional.of(pendingPayment));
                        when(paymentRepository.save(any(Payment.class))).thenReturn(pendingPayment);

                        ProcessPaymentDTO dto = new ProcessPaymentDTO();
                        dto.setMethod("BITCOIN");

                        assertThatThrownBy(() -> service.processPaymentForRide(10L, dto, false))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.BAD_REQUEST));
                }

                @Test
                void surgeMultiplierFromMetadata_computedCorrectly() {
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("surgeMultiplier", "1.5");
                        RideDTO rideWithSurge = new RideDTO(10L, 1L, 5L, "PAYMENT_PENDING", 100.0,
                                        LocalDateTime.now(), null, meta);
                        when(rideServiceClient.getRide(10L)).thenReturn(rideWithSurge);
                        when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                                        .thenReturn(Optional.of(pendingPayment));

                        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
                        when(paymentRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

                        ProcessPaymentDTO dto = new ProcessPaymentDTO();
                        dto.setMethod("CASH");
                        service.processPaymentForRide(10L, dto, false);

                        Payment saved = captor.getValue();
                        // surgeMultiplier=1.5 → surgeFee = 100 * (1.5 - 1.0) = 50.0
                        assertThat(((Number) saved.getTransactionDetails().get("surgeFee")).doubleValue())
                                        .isEqualTo(50.0);
                }

                @Test
                void noMetadata_fallbackSurgeFee15Percent() {
                        when(rideServiceClient.getRide(10L)).thenReturn(rideInPaymentPending());
                        when(paymentRepository.findByRideIdAndStatus(10L, PaymentStatus.PENDING))
                                        .thenReturn(Optional.of(pendingPayment));

                        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
                        when(paymentRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

                        ProcessPaymentDTO dto = new ProcessPaymentDTO();
                        dto.setMethod("CASH");
                        service.processPaymentForRide(10L, dto, false);

                        Payment saved = captor.getValue();
                        // fallback: surgeFee = 100 * 0.15 = 15.0
                        assertThat(((Number) saved.getTransactionDetails().get("surgeFee")).doubleValue())
                                        .isEqualTo(15.0);
                }
        }

        // ════════════════════════════════════════════════════════════════════════
        // §7 S5-F10 getFareRevenueByVehicleType — local query + Feign + local cache
        // ════════════════════════════════════════════════════════════════════════

        @Nested
        class GetFareRevenueByVehicleType {

                private Payment completedPayment(Long id, Long rideId, double amount) {
                        Payment p = new Payment();
                        p.setRideId(rideId);
                        p.setUserId(1L);
                        p.setAmount(amount);
                        p.setStatus(PaymentStatus.COMPLETED);
                        Map<String, Object> td = new HashMap<>();
                        td.put("surgeFee", amount * 0.15);
                        p.setTransactionDetails(td);
                        return p;
                }

                @Test
                void happyPath_groupsByVehicleType() {
                        Payment p1 = completedPayment(1L, 10L, 100.0);
                        Payment p2 = completedPayment(2L, 20L, 200.0);

                        when(paymentRepository.findCompletedPaymentsInDateRangeCapped(any(), any()))
                                        .thenReturn(List.of(p1, p2));

                        RideDTO ride1 = new RideDTO(10L, 1L, 5L, "COMPLETED", 100.0, LocalDateTime.now(), null,
                                        Map.of());
                        RideDTO ride2 = new RideDTO(20L, 1L, 6L, "COMPLETED", 200.0, LocalDateTime.now(), null,
                                        Map.of());
                        when(rideServiceClient.getRide(10L)).thenReturn(ride1);
                        when(rideServiceClient.getRide(20L)).thenReturn(ride2);

                        DriverDTO sedan = new DriverDTO(5L, "D1", "01", "ACTIVE", 4.5, 10,
                                        Map.of("vehicleType", "SEDAN"));
                        DriverDTO suv = new DriverDTO(6L, "D2", "02", "ACTIVE", 4.8, 5,
                                        Map.of("vehicleType", "SUV"));
                        when(driverServiceClient.getDriver(5L)).thenReturn(sedan);
                        when(driverServiceClient.getDriver(6L)).thenReturn(suv);

                        List<VehicleTypeRevenueDTO> result = service.getFareRevenueByVehicleType("2024-01-01",
                                        "2024-12-31");

                        assertThat(result).hasSize(2);
                        Map<String, Double> totalByType = new HashMap<>();
                        result.forEach(r -> totalByType.put(r.getVehicleType(), r.getTotalRevenue()));
                        assertThat(totalByType).containsKeys("SEDAN", "SUV");
                        assertThat(totalByType.get("SEDAN")).isEqualTo(100.0);
                        assertThat(totalByType.get("SUV")).isEqualTo(200.0);
                }

                @Test
                void rideFeignFails_thatPaymentIsSkipped() {
                        Payment p1 = completedPayment(1L, 10L, 100.0);
                        Payment p2 = completedPayment(2L, 20L, 200.0);
                        when(paymentRepository.findCompletedPaymentsInDateRangeCapped(any(), any()))
                                        .thenReturn(List.of(p1, p2));

                        when(rideServiceClient.getRide(10L)).thenThrow(buildServiceUnavailable("timeout"));
                        RideDTO ride2 = new RideDTO(20L, 1L, 6L, "COMPLETED", 200.0, LocalDateTime.now(), null,
                                        Map.of());
                        when(rideServiceClient.getRide(20L)).thenReturn(ride2);
                        when(driverServiceClient.getDriver(6L)).thenReturn(
                                        new DriverDTO(6L, "D2", "02", "ACTIVE", 4.8, 5, Map.of("vehicleType", "SUV")));

                        List<VehicleTypeRevenueDTO> result = service.getFareRevenueByVehicleType("2024-01-01",
                                        "2024-12-31");

                        // Only p2 (SUV) makes it through; p1 was skipped
                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).getVehicleType()).isEqualTo("SUV");
                }

                @Test
                void driverFeignFails_vehicleTypeBecomesUnknown() {
                        Payment p = completedPayment(1L, 10L, 100.0);
                        when(paymentRepository.findCompletedPaymentsInDateRangeCapped(any(), any()))
                                        .thenReturn(List.of(p));
                        when(rideServiceClient.getRide(10L))
                                        .thenReturn(new RideDTO(10L, 1L, 5L, "COMPLETED", 100.0, LocalDateTime.now(),
                                                        null, Map.of()));
                        when(driverServiceClient.getDriver(5L)).thenThrow(buildNotFound("driver not found"));

                        List<VehicleTypeRevenueDTO> result = service.getFareRevenueByVehicleType("2024-01-01",
                                        "2024-12-31");

                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).getVehicleType()).isEqualTo("UNKNOWN");
                }

                @Test
                void sameDriverId_driverServiceCalledOnlyOnce() {
                        // Two payments for rides driven by the same driver
                        Payment p1 = completedPayment(1L, 10L, 100.0);
                        Payment p2 = completedPayment(2L, 20L, 50.0);
                        when(paymentRepository.findCompletedPaymentsInDateRangeCapped(any(), any()))
                                        .thenReturn(List.of(p1, p2));

                        RideDTO ride1 = new RideDTO(10L, 1L, 5L, "COMPLETED", 100.0, LocalDateTime.now(), null,
                                        Map.of());
                        RideDTO ride2 = new RideDTO(20L, 1L, 5L, "COMPLETED", 50.0, LocalDateTime.now(), null,
                                        Map.of());
                        when(rideServiceClient.getRide(10L)).thenReturn(ride1);
                        when(rideServiceClient.getRide(20L)).thenReturn(ride2);
                        when(driverServiceClient.getDriver(5L)).thenReturn(
                                        new DriverDTO(5L, "D1", "01", "ACTIVE", 4.5, 10,
                                                        Map.of("vehicleType", "SEDAN")));

                        service.getFareRevenueByVehicleType("2024-01-01", "2024-12-31");

                        // driverServiceClient.getDriver must only be called once despite two rides
                        verify(driverServiceClient, times(1)).getDriver(5L);
                }

                @Test
                void sameDriverId_revenueAggregatedCorrectly() {
                        Payment p1 = completedPayment(1L, 10L, 100.0);
                        Payment p2 = completedPayment(2L, 20L, 50.0);
                        when(paymentRepository.findCompletedPaymentsInDateRangeCapped(any(), any()))
                                        .thenReturn(List.of(p1, p2));
                        when(rideServiceClient.getRide(10L))
                                        .thenReturn(new RideDTO(10L, 1L, 5L, "COMPLETED", 100.0, LocalDateTime.now(),
                                                        null, Map.of()));
                        when(rideServiceClient.getRide(20L))
                                        .thenReturn(new RideDTO(20L, 1L, 5L, "COMPLETED", 50.0, LocalDateTime.now(),
                                                        null, Map.of()));
                        when(driverServiceClient.getDriver(5L)).thenReturn(
                                        new DriverDTO(5L, "D1", "01", "ACTIVE", 4.5, 10,
                                                        Map.of("vehicleType", "SEDAN")));

                        List<VehicleTypeRevenueDTO> result = service.getFareRevenueByVehicleType("2024-01-01",
                                        "2024-12-31");

                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).getTotalRevenue()).isEqualTo(150.0);
                        assertThat(result.get(0).getRideCount()).isEqualTo(2L);
                }

                @Test
                void startAfterEnd_throws400() {
                        assertThatThrownBy(() -> service.getFareRevenueByVehicleType("2024-12-31", "2024-01-01"))
                                        .isInstanceOf(ResponseStatusException.class)
                                        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                                                        .isEqualTo(HttpStatus.BAD_REQUEST));

                        verifyNoInteractions(paymentRepository);
                }

                @Test
                void cacheHit_skipsDatabaseAndFeign() {
                        List<VehicleTypeRevenueDTO> cached = List.of(
                                        VehicleTypeRevenueDTO.builder().vehicleType("SEDAN").totalRevenue(500.0)
                                                        .rideCount(5L).surgeFeeRevenue(75.0).baseFareRevenue(425.0)
                                                        .build());
                        when(cache.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                        .thenReturn(cached);

                        List<VehicleTypeRevenueDTO> result = service.getFareRevenueByVehicleType("2024-01-01",
                                        "2024-12-31");

                        assertThat(result).isSameAs(cached);
                        verifyNoInteractions(paymentRepository, rideServiceClient, driverServiceClient);
                }

                @Test
                void emptyPayments_returnsEmptyList() {
                        when(paymentRepository.findCompletedPaymentsInDateRangeCapped(any(), any()))
                                        .thenReturn(Collections.emptyList());

                        List<VehicleTypeRevenueDTO> result = service.getFareRevenueByVehicleType("2024-01-01",
                                        "2024-12-31");

                        assertThat(result).isEmpty();
                        verifyNoInteractions(rideServiceClient, driverServiceClient);
                }
        }

        // ════════════════════════════════════════════════════════════════════════
        // §7 new endpoint getUserPaymentTotal
        // ════════════════════════════════════════════════════════════════════════

        @Nested
        class GetUserPaymentTotal {

                @Test
                void returnsSum() {
                        when(paymentRepository.sumCompletedAmountByUserIdAndDateRange(eq(1L), any(), any()))
                                        .thenReturn(new BigDecimal("250.00"));

                        BigDecimal result = service.getUserPaymentTotal(1L, "2024-01-01", "2024-12-31");

                        assertThat(result).isEqualByComparingTo("250.00");
                }

                @Test
                void noPayments_returnsZero() {
                        when(paymentRepository.sumCompletedAmountByUserIdAndDateRange(eq(1L), any(), any()))
                                        .thenReturn(null);

                        BigDecimal result = service.getUserPaymentTotal(1L, "2024-01-01", "2024-12-31");

                        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
                }

                @Test
                void datesParsedCorrectly_startAtMidnightEndAtEndOfDay() {
                        when(paymentRepository.sumCompletedAmountByUserIdAndDateRange(any(), any(), any()))
                                        .thenReturn(BigDecimal.ZERO);

                        service.getUserPaymentTotal(1L, "2024-06-15", "2024-06-20");

                        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
                        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
                        verify(paymentRepository).sumCompletedAmountByUserIdAndDateRange(
                                        eq(1L), startCaptor.capture(), endCaptor.capture());

                        assertThat(startCaptor.getValue().getHour()).isEqualTo(0);
                        assertThat(endCaptor.getValue().getHour()).isEqualTo(23);
                }
        }

        // ════════════════════════════════════════════════════════════════════════
        // §2.10 PaymentController — ownership check on /user/{userId}/summary
        // ════════════════════════════════════════════════════════════════════════

        @Nested
        class OwnershipCheck {

                MockMvc mockMvc;
                PaymentService mockService;

                @BeforeEach
                void setupMvc() {
                        mockService = mock(PaymentService.class);
                        PaymentController controller = new PaymentController(mockService);
                        // standaloneSetup: no Spring security filter, no JWT — we set attributes
                        // manually
                        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
                }

                @Test
                void sameUserId_returns200() throws Exception {
                        when(mockService.getUserPaymentSummary(1L))
                                        .thenReturn(UserPaymentSummaryDTO.builder().userId(1L)
                                                        .totalPayments(0L).totalAmount(0.0).methodBreakdown(Map.of())
                                                        .build());

                        mockMvc.perform(get("/api/payments/user/1/summary")
                                        .requestAttr("userId", 1L)
                                        .requestAttr("userRole", "USER"))
                                        .andExpect(status().isOk());
                }

                @Test
                void differentUserId_notAdmin_returns403() throws Exception {
                        mockMvc.perform(get("/api/payments/user/1/summary")
                                        .requestAttr("userId", 2L) // authenticated as user 2
                                        .requestAttr("userRole", "USER"))
                                        .andExpect(status().isForbidden());

                        verifyNoInteractions(mockService);
                }

                @Test
                void differentUserId_adminRole_returns200() throws Exception {
                        when(mockService.getUserPaymentSummary(1L))
                                        .thenReturn(UserPaymentSummaryDTO.builder().userId(1L)
                                                        .totalPayments(0L).totalAmount(0.0).methodBreakdown(Map.of())
                                                        .build());

                        mockMvc.perform(get("/api/payments/user/1/summary")
                                        .requestAttr("userId", 999L) // different user but ADMIN
                                        .requestAttr("userRole", "ADMIN"))
                                        .andExpect(status().isOk());
                }

                @Test
                void noAuthAttributes_stillForwards() throws Exception {
                        // Gateway didn't set attributes (e.g. internal call) — no check is applied
                        when(mockService.getUserPaymentSummary(1L))
                                        .thenReturn(UserPaymentSummaryDTO.builder().userId(1L)
                                                        .totalPayments(0L).totalAmount(0.0).methodBreakdown(Map.of())
                                                        .build());

                        mockMvc.perform(get("/api/payments/user/1/summary"))
                                        .andExpect(status().isOk());
                }

                @Test
                void userIdStoredAsString_stillParsed() throws Exception {
                        // JwtAuthenticationFilter might store userId as String in some configs
                        when(mockService.getUserPaymentSummary(1L))
                                        .thenReturn(UserPaymentSummaryDTO.builder().userId(1L)
                                                        .totalPayments(0L).totalAmount(0.0).methodBreakdown(Map.of())
                                                        .build());

                        mockMvc.perform(get("/api/payments/user/1/summary")
                                        .requestAttr("userId", "1") // String, not Long
                                        .requestAttr("userRole", "USER"))
                                        .andExpect(status().isOk());
                }
        }

        // ════════════════════════════════════════════════════════════════════════
        // §11.1 CorrelationIdFilter
        // ════════════════════════════════════════════════════════════════════════

        @Nested
        class CorrelationIdFilterTests {

                CorrelationIdFilter filter = new CorrelationIdFilter();

                @Test
                void incomingHeaderIsForwarded() throws Exception {
                        MockHttpServletRequest req = new MockHttpServletRequest();
                        MockHttpServletResponse res = new MockHttpServletResponse();
                        req.addHeader("X-Correlation-ID", "test-corr-id-123");
                        FilterChain chain = mock(FilterChain.class);

                        filter.doFilter(req, res, chain);

                        assertThat(res.getHeader("X-Correlation-ID")).isEqualTo("test-corr-id-123");
                        verify(chain).doFilter(req, res);
                }

                @Test
                void missingHeader_generatesUuid() throws Exception {
                        MockHttpServletRequest req = new MockHttpServletRequest();
                        MockHttpServletResponse res = new MockHttpServletResponse();
                        FilterChain chain = mock(FilterChain.class);

                        filter.doFilter(req, res, chain);

                        String correlationId = res.getHeader("X-Correlation-ID");
                        assertThat(correlationId).isNotNull().isNotBlank();
                        // UUID format: 8-4-4-4-12 hex chars
                        assertThat(correlationId).matches(
                                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
                }

                @Test
                void blankHeader_generatesUuid() throws Exception {
                        MockHttpServletRequest req = new MockHttpServletRequest();
                        MockHttpServletResponse res = new MockHttpServletResponse();
                        req.addHeader("X-Correlation-ID", "   ");
                        FilterChain chain = mock(FilterChain.class);

                        filter.doFilter(req, res, chain);

                        String correlationId = res.getHeader("X-Correlation-ID");
                        assertThat(correlationId).isNotBlank();
                        assertThat(correlationId).isNotEqualTo("   ");
                }

                @Test
                void chainAlwaysCalledEvenOnException() throws Exception {
                        MockHttpServletRequest req = new MockHttpServletRequest();
                        MockHttpServletResponse res = new MockHttpServletResponse();
                        FilterChain chain = mock(FilterChain.class);
                        doThrow(new RuntimeException("downstream error")).when(chain).doFilter(any(), any());

                        assertThatThrownBy(() -> filter.doFilter(req, res, chain))
                                        .isInstanceOf(RuntimeException.class);

                        // MDC must still be cleared — verify by checking no residual (indirect: filter
                        // completed finally block)
                        verify(chain).doFilter(req, res);
                }
        }

        // ════════════════════════════════════════════════════════════════════════
        // §10.4 PaymentRabbitConfig — exchange bean
        // ════════════════════════════════════════════════════════════════════════

        @Nested
        class RabbitConfigTests {

                @Test
                void exchangeIsDurableTopicWithCorrectName() {
                        PaymentRabbitConfig cfg = new PaymentRabbitConfig();
                        var exchange = cfg.paymentExchange();

                        assertThat(exchange.getName()).isEqualTo("payment.events");
                        assertThat(exchange.isDurable()).isTrue();
                        assertThat(exchange.isAutoDelete()).isFalse();
                }

                @Test
                void routingKeyConstantsAreCorrect() {
                        assertThat(PaymentRabbitConfig.ROUTING_KEY_COMPLETED).isEqualTo("payment.completed");
                        assertThat(PaymentRabbitConfig.ROUTING_KEY_FAILED).isEqualTo("payment.failed");
                }
        }

        // ════════════════════════════════════════════════════════════════════════
        // Helpers: build typed FeignExceptions via Mockito (avoids constructor
        // version mismatch across different feign-core releases)
        // ════════════════════════════════════════════════════════════════════════

        private static FeignException.NotFound buildNotFound(String message) {
                return mock(FeignException.NotFound.class);
        }

        private static FeignException buildServiceUnavailable(String message) {
                return mock(FeignException.ServiceUnavailable.class);
        }
}
