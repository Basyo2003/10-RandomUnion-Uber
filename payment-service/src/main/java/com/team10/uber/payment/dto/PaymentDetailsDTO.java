package com.team10.uber.payment.dto;

import com.team10.uber.payment.enums.PaymentMethod;
import com.team10.uber.payment.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PaymentDetailsDTO {

    private Long paymentId;
    private Long rideId;
    private Long userId;
    private Double originalAmount;
    private PaymentMethod method;
    private PaymentStatus status;
    private Map<String, Object> transactionDetails;
    private List<AppliedCouponDTO> appliedCoupons;
    private Double totalDiscount;
    private Double finalAmount;

    public static class AppliedCouponDTO {
        private String couponCode;
        private String discountType;
        private Double discountApplied;
        private LocalDateTime appliedAt;

        public AppliedCouponDTO(String couponCode, String discountType,
                                Double discountApplied, LocalDateTime appliedAt) {
            this.couponCode      = couponCode;
            this.discountType    = discountType;
            this.discountApplied = discountApplied;
            this.appliedAt       = appliedAt;
        }

        public String getCouponCode()           { return couponCode; }
        public String getDiscountType()         { return discountType; }
        public Double getDiscountApplied()      { return discountApplied; }
        public LocalDateTime getAppliedAt()     { return appliedAt; }
    }

    public PaymentDetailsDTO(Long paymentId, Long rideId, Long userId, Double originalAmount, PaymentMethod method, PaymentStatus status, Map<String, Object> transactionDetails, List<AppliedCouponDTO> appliedCoupons, Double totalDiscount, Double finalAmount) {
        this.paymentId          = paymentId;
        this.rideId             = rideId;
        this.userId             = userId;
        this.originalAmount     = originalAmount;
        this.method             = method;
        this.status             = status;
        this.transactionDetails = transactionDetails;
        this.appliedCoupons     = appliedCoupons;
        this.totalDiscount      = totalDiscount;
        this.finalAmount        = finalAmount;
    }

    public Long getPaymentId() {
        return paymentId; 
    }

    public Long getRideId() {
        return rideId;
    }

    public Long getUserId() { 
        return userId;
    }

    public Double getOriginalAmount() {
        return originalAmount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Map<String, Object> getTransactionDetails() {
        return transactionDetails;
    }

    public List<AppliedCouponDTO> getAppliedCoupons() {
        return appliedCoupons;
    }

    public Double getTotalDiscount() {
        return totalDiscount;
    }
    
    public Double getFinalAmount() {
        return finalAmount;
    }
}