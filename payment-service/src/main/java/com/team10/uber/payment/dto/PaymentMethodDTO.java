package com.team10.uber.payment.dto;

public class PaymentMethodDTO {

    private String method;
    private long successCount;
    private long failureCount;
    private double successRate;
    private double totalAmount;

    private PaymentMethodDTO() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final PaymentMethodDTO dto = new PaymentMethodDTO();

        public Builder method(String method) { dto.method = method; return this; }
        public Builder successCount(long c) { dto.successCount = c; return this; }
        public Builder failureCount(long c) { dto.failureCount = c; return this; }
        public Builder successRate(double r) { dto.successRate = r; return this; }
        public Builder totalAmount(double a) { dto.totalAmount = a; return this; }
        public PaymentMethodDTO build() { return dto; }
    }

    public String getMethod() { return method; }
    public long getSuccessCount() { return successCount; }
    public long getFailureCount() { return failureCount; }
    public double getSuccessRate() { return successRate; }
    public double getTotalAmount() { return totalAmount; }
}
