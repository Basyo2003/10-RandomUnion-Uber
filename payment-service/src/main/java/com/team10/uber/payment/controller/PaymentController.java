package com.team10.uber.payment.controller;

import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Map;
import com.team10.uber.payment.strategy.RefundRequest;

import com.team10.uber.payment.dto.UserPaymentSummaryDTO;
import com.team10.uber.payment.dto.ProcessPaymentDTO;
import com.team10.uber.payment.dto.CouponUsageDTO;
import com.team10.uber.payment.dto.PaymentMethodDTO;
import com.team10.uber.payment.dto.PaymentDetailsDTO;
import com.team10.uber.payment.dto.RevenueReportDTO;
import com.team10.uber.payment.dto.VehicleTypeRevenueDTO;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody Payment payment) {
        Payment created = paymentService.createPayment(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Payment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Payment> updatePayment(@PathVariable Long id,
            @RequestBody Payment payment) {
        return ResponseEntity.ok(paymentService.updatePayment(id, payment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Payment> refundPayment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        return ResponseEntity.ok(paymentService.refundPayment(id, reason));
    }

    // §2.10: ownership check — caller must be the user themselves or ADMIN
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserPaymentSummaryDTO> getUserPaymentSummary(
            @PathVariable Long userId,
            HttpServletRequest request) {
        Object rawAuthId = request.getAttribute("userId");
        if (rawAuthId != null) {
            Long authUserId = rawAuthId instanceof Long ? (Long) rawAuthId
                    : Long.parseLong(rawAuthId.toString());
            String role = (String) request.getAttribute("userRole");
            if (!"ADMIN".equals(role) && !authUserId.equals(userId)) {
                log.warn("getUserPaymentSummary: forbidden access attempt authUserId={} on userId={}", authUserId, userId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }
        log.info("getUserPaymentSummary: userId={}", userId);
        return ResponseEntity.ok(paymentService.getUserPaymentSummary(userId));
    }

    // S5-READ-DB: consumed by user-service via PaymentServiceClient Feign interface
    @GetMapping("/user/{userId}/total")
    public ResponseEntity<BigDecimal> getUserPaymentTotal(
            @PathVariable Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        log.info("getUserPaymentTotal: userId={}, startDate={}, endDate={}", userId, startDate, endDate);
        return ResponseEntity.ok(paymentService.getUserPaymentTotal(userId, startDate, endDate));
    }

    @PostMapping("/ride/{rideId}")
    public ResponseEntity<Payment> processPaymentForRide(
            @PathVariable Long rideId,
            @RequestBody ProcessPaymentDTO dto,
            @RequestParam(defaultValue = "false") boolean simulateFailure) {
        log.info("processPaymentForRide: rideId={}, simulateFailure={}", rideId, simulateFailure);
        Payment payment = paymentService.processPaymentForRide(rideId, dto, simulateFailure);
        log.info("processPaymentForRide: completed paymentId={}, status={}", payment.getId(), payment.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }


    @GetMapping("/search")
    public ResponseEntity<List<Payment>> searchPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long userId) {

        LocalDateTime start = startDate != null ? paymentService.parseDate(startDate, false) : null;
        LocalDateTime end   = endDate   != null ? paymentService.parseDate(endDate,   true)  : null;
        List<Payment> payments = paymentService.searchPayments(status, start, end, userId);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDateTime start = paymentService.parseDate(startDate, false);
        LocalDateTime end = paymentService.parseDate(endDate, true);
        return ResponseEntity.ok(paymentService.getRevenueReport(start, end));
    }

    @PutMapping("/{id}/retry")
    public ResponseEntity<Payment> retryPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.retryPayment(id));
    }

    @GetMapping("/{paymentId}/details")
    public ResponseEntity<PaymentDetailsDTO> getPaymentDetails(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentDetails(paymentId));
    }

    @GetMapping("/coupons/top-used")
    public ResponseEntity<List<CouponUsageDTO>> getTopUsedCoupons(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ResponseEntity.ok(paymentService.getTopUsedCoupons(limit));
    }

    @PostMapping("/{paymentId}/coupons/{couponId}")
    public ResponseEntity<PaymentDetailsDTO> applyCoupon(
            @PathVariable Long paymentId,
            @PathVariable Long couponId) {
        PaymentDetailsDTO result = paymentService.applyCoupon(paymentId, couponId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/refund-surge-adjusted")
    public ResponseEntity<Payment> refundSurgeAdjusted(
            @PathVariable Long id,
            @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refundSurgeAdjusted(id, request));
    }

    @GetMapping("/analytics/vehicle-type")
    public ResponseEntity<List<VehicleTypeRevenueDTO>> getFareRevenueByVehicleType(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        log.info("getFareRevenueByVehicleType: startDate={}, endDate={}", startDate, endDate);
        List<VehicleTypeRevenueDTO> result = paymentService.getFareRevenueByVehicleType(startDate, endDate);
        log.info("getFareRevenueByVehicleType: returned {} vehicle types", result.size());
        return ResponseEntity.ok(result);
    }

    // §10.5.2 — S5-F11
    @GetMapping("/analytics/methods")
    public ResponseEntity<List<PaymentMethodDTO>> getPaymentMethodBreakdown(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ResponseEntity.ok(paymentService.getPaymentMethodBreakdown(startDate, endDate));
    }
}