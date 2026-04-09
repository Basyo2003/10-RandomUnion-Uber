package com.team10.uber.payment.controller;

import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

import com.team10.uber.payment.dto.UserPaymentSummaryDTO;
import com.team10.uber.payment.dto.PaymentDetailsDTO;
import com.team10.uber.payment.dto.RevenueReportDTO;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

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

    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserPaymentSummaryDTO> getUserPaymentSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.getUserPaymentSummary(userId));
    }

    @PostMapping("/{paymentId}/coupons/{couponId}")
    public ResponseEntity<PaymentDetailsDTO> applyCoupon(
            @PathVariable Long paymentId,
            @PathVariable Long couponId) {
        PaymentDetailsDTO result = paymentService.applyCoupon(paymentId, couponId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Payment>> searchPayments(
            @RequestParam(required = false) String status,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");

        List<Payment> payments = paymentService.searchPayments(status, start, end);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(@RequestParam String startDate, @RequestParam String endDate) {
        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end   = LocalDateTime.parse(endDate   + "T23:59:59");
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
}