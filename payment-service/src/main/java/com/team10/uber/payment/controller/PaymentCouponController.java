package com.team10.uber.payment.controller;

import com.team10.uber.payment.model.PaymentCoupon;
import com.team10.uber.payment.service.PaymentCouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-coupons")
public class PaymentCouponController {

    private final PaymentCouponService paymentCouponService;

    public PaymentCouponController(PaymentCouponService paymentCouponService) {
        this.paymentCouponService = paymentCouponService;
    }

    @PostMapping
    public ResponseEntity<PaymentCoupon> createPaymentCoupon(
            @RequestParam Long paymentId,
            @RequestParam Long couponId,
            @RequestBody PaymentCoupon pc) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentCouponService.createPaymentCoupon(paymentId, couponId, pc));
    }

    @GetMapping
    public ResponseEntity<List<PaymentCoupon>> getAllPaymentCoupons() {
        return ResponseEntity.ok(paymentCouponService.getAllPaymentCoupons());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentCoupon> getPaymentCouponById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentCouponService.getPaymentCouponById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentCoupon> updatePaymentCoupon(
            @PathVariable Long id,
            @RequestBody PaymentCoupon paymentCoupon) {

        return ResponseEntity.ok(
                paymentCouponService.updatePaymentCoupon(id, paymentCoupon));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePaymentCoupon(@PathVariable Long id) {
        paymentCouponService.deletePaymentCoupon(id);
        return ResponseEntity.noContent().build();
    }
}
