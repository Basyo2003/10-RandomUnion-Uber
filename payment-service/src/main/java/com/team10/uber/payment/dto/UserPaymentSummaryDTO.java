package com.team10.uber.payment.dto;

import java.util.Map;

public class UserPaymentSummaryDTO {

    private Long userId;
    private Long totalPayments;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

    public UserPaymentSummaryDTO() {}

    public UserPaymentSummaryDTO(Long userId, Long totalPayments, Double totalAmount,
                                 Map<String, Double> methodBreakdown) {
        this.userId = userId;
        this.totalPayments = totalPayments;
        this.totalAmount = totalAmount;
        this.methodBreakdown = methodBreakdown;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long userId;
        private Long totalPayments;
        private Double totalAmount;
        private Map<String, Double> methodBreakdown;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder totalPayments(Long totalPayments) { this.totalPayments = totalPayments; return this; }
        public Builder totalAmount(Double totalAmount) { this.totalAmount = totalAmount; return this; }
        public Builder methodBreakdown(Map<String, Double> methodBreakdown) { this.methodBreakdown = methodBreakdown; return this; }

        public UserPaymentSummaryDTO build() {
            return new UserPaymentSummaryDTO(userId, totalPayments, totalAmount, methodBreakdown);
        }
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTotalPayments() { return totalPayments; }
    public void setTotalPayments(Long totalPayments) { this.totalPayments = totalPayments; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public Map<String, Double> getMethodBreakdown() { return methodBreakdown; }
    public void setMethodBreakdown(Map<String, Double> methodBreakdown) { this.methodBreakdown = methodBreakdown; }
}