package com.team10.uber.payment.dto;

import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.model.PaymentCoupon;

import java.util.List;

public class PaymentWithCouponsDTO {

    private Payment payment;
    private List<PaymentCoupon> appliedCoupons;

    public PaymentWithCouponsDTO(Payment payment, List<PaymentCoupon> appliedCoupons) {
        this.payment = payment;
        this.appliedCoupons = appliedCoupons;
    }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }

    public List<PaymentCoupon> getAppliedCoupons() { return appliedCoupons; }
    public void setAppliedCoupons(List<PaymentCoupon> appliedCoupons) { this.appliedCoupons = appliedCoupons; }
}