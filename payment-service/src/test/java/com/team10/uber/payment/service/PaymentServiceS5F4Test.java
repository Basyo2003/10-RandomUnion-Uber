package com.team10.uber.payment.service;

import com.team10.uber.payment.dto.ProcessPaymentDTO;
import com.team10.uber.payment.enums.PaymentStatus;
import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.observer.EntityObserver;
import com.team10.uber.payment.observer.MongoEventLogger;
import com.team10.uber.payment.cache.RedisCacheService;
import com.team10.uber.payment.repository.CouponRepository;
import com.team10.uber.payment.repository.PaymentCouponRepository;
import com.team10.uber.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceS5F4Test {

    private PaymentService svc;
    private PaymentRepository paymentRepo;
    private EntityObserver mockObserver;

    @BeforeEach
    void setUp() {
        paymentRepo = mock(PaymentRepository.class);
        CouponRepository couponRepo = mock(CouponRepository.class);
        PaymentCouponRepository couponPaymentRepo = mock(PaymentCouponRepository.class);
        MongoEventLogger mongoLogger = mock(MongoEventLogger.class);
        RedisCacheService cacheService = mock(RedisCacheService.class);

        svc = new PaymentService(paymentRepo, couponRepo, couponPaymentRepo, mongoLogger, cacheService);

        mockObserver = mock(EntityObserver.class);
        svc.register(mockObserver);

        when(paymentRepo.findRideStatusById(1L)).thenReturn("COMPLETED");
        when(paymentRepo.findByRideIdAndStatus(1L, PaymentStatus.COMPLETED)).thenReturn(Optional.empty());
        when(paymentRepo.findRideFareById(1L)).thenReturn(100.0);
        when(paymentRepo.findRideUserIdById(1L)).thenReturn(42L);
        when(paymentRepo.findSurgeMultiplierByRideId(1L)).thenReturn(null);
        when(paymentRepo.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(99L);
            return p;
        });
    }

    @Test
    void processPaymentForRide_firesBothCreatedAndCompletedEvents() {
        ProcessPaymentDTO dto = new ProcessPaymentDTO();
        dto.setMethod("CASH");

        svc.processPaymentForRide(1L, dto, false);

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockObserver, times(2)).onEvent(eventCaptor.capture(), any());
        List<String> events = eventCaptor.getAllValues();
        assertEquals("CREATED",   events.get(0), "First event must be CREATED");
        assertEquals("COMPLETED", events.get(1), "Second event must be COMPLETED");
    }

    @Test
    void processPaymentForRide_withSimulateFailure_firesFailed() {
        ProcessPaymentDTO dto = new ProcessPaymentDTO();
        dto.setMethod("CASH");

        svc.processPaymentForRide(1L, dto, true);

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockObserver, times(2)).onEvent(eventCaptor.capture(), any());
        List<String> events = eventCaptor.getAllValues();
        assertEquals("CREATED", events.get(0));
        assertEquals("FAILED",  events.get(1));
    }

    @Test
    void paymentController_acceptsSimulateFailureParam() throws Exception {
        var method = com.team10.uber.payment.controller.PaymentController.class
            .getMethod("processPaymentForRide",
                Long.class,
                com.team10.uber.payment.dto.ProcessPaymentDTO.class,
                boolean.class);
        assertNotNull(method, "processPaymentForRide must accept boolean simulateFailure param");
    }
}
