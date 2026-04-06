package com.team10.uber.payment.dto;

import java.util.Map;

public class UserPaymentSummaryDTO {

    private Long userId;
    private Long totalPayments;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

    public UserPaymentSummaryDTO(Long userId, Long totalPayments, Double totalAmount, Map<String, Double> methodBreakdown) {
        this.userId = userId;
        this.totalPayments = totalPayments;
        this.totalAmount = totalAmount;
        this.methodBreakdown = methodBreakdown;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTotalPayments() { return totalPayments; }
    public void setTotalPayments(Long totalPayments) { this.totalPayments = totalPayments; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Map<String, Double> getMethodBreakdown() { return methodBreakdown; }
    public void setMethodBreakdown(Map<String, Double> methodBreakdown) { this.methodBreakdown = methodBreakdown; }
}