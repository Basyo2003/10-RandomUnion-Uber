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

    public PaymentDetailsDTO() {}

    public PaymentDetailsDTO(Long paymentId, Long rideId, Long userId, Double originalAmount,
                             PaymentMethod method, PaymentStatus status,
                             Map<String, Object> transactionDetails,
                             List<AppliedCouponDTO> appliedCoupons,
                             Double totalDiscount, Double finalAmount) {
        this.paymentId = paymentId;
        this.rideId = rideId;
        this.userId = userId;
        this.originalAmount = originalAmount;
        this.method = method;
        this.status = status;
        this.transactionDetails = transactionDetails;
        this.appliedCoupons = appliedCoupons;
        this.totalDiscount = totalDiscount;
        this.finalAmount = finalAmount;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
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

        public Builder paymentId(Long paymentId) { this.paymentId = paymentId; return this; }
        public Builder rideId(Long rideId) { this.rideId = rideId; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder originalAmount(Double originalAmount) { this.originalAmount = originalAmount; return this; }
        public Builder method(PaymentMethod method) { this.method = method; return this; }
        public Builder status(PaymentStatus status) { this.status = status; return this; }
        public Builder transactionDetails(Map<String, Object> transactionDetails) { this.transactionDetails = transactionDetails; return this; }
        public Builder appliedCoupons(List<AppliedCouponDTO> appliedCoupons) { this.appliedCoupons = appliedCoupons; return this; }
        public Builder totalDiscount(Double totalDiscount) { this.totalDiscount = totalDiscount; return this; }
        public Builder finalAmount(Double finalAmount) { this.finalAmount = finalAmount; return this; }

        public PaymentDetailsDTO build() {
            return new PaymentDetailsDTO(paymentId, rideId, userId, originalAmount, method, status,
                    transactionDetails, appliedCoupons, totalDiscount, finalAmount);
        }
    }

    public static class AppliedCouponDTO {
        private String couponCode;
        private String discountType;
        private Double discountApplied;
        private LocalDateTime appliedAt;

        public AppliedCouponDTO() {}

        public AppliedCouponDTO(String couponCode, String discountType,
                                Double discountApplied, LocalDateTime appliedAt) {
            this.couponCode = couponCode;
            this.discountType = discountType;
            this.discountApplied = discountApplied;
            this.appliedAt = appliedAt;
        }

        public String getCouponCode() { return couponCode; }
        public String getDiscountType() { return discountType; }
        public Double getDiscountApplied() { return discountApplied; }
        public LocalDateTime getAppliedAt() { return appliedAt; }
    }

    public Long getPaymentId() { return paymentId; }
    public Long getRideId() { return rideId; }
    public Long getUserId() { return userId; }
    public Double getOriginalAmount() { return originalAmount; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
    public List<AppliedCouponDTO> getAppliedCoupons() { return appliedCoupons; }
    public Double getTotalDiscount() { return totalDiscount; }
    public Double getFinalAmount() { return finalAmount; }
}