package com.team10.uber.payment.controller;

import com.team10.uber.payment.model.Coupon;
import com.team10.uber.payment.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping
    public ResponseEntity<Coupon> createCoupon(@RequestBody Coupon coupon) {
        Coupon created = couponService.createCoupon(coupon);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Coupon>> getAllCoupons() {
        return ResponseEntity.ok(couponService.getAllCoupons());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Coupon> getCouponById(@PathVariable Long id) {
        return ResponseEntity.ok(couponService.getCouponById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Coupon> updateCoupon(@PathVariable Long id,
                                               @RequestBody Coupon coupon) {
        return ResponseEntity.ok(couponService.updateCoupon(id, coupon));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.noContent().build();
    }
}
