package com.team10.uber.payment.dto;

public class PaymentMethodDTO {

    private String method;
    private long successCount;
    private long failureCount;
    private double successRate;
    private double totalAmount;

    // Required for Builder pattern (DP-4)
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String method;
        private long successCount;
        private long failureCount;
        private double successRate;
        private double totalAmount;

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder successCount(long successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder failureCount(long failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        public Builder successRate(double successRate) {
            this.successRate = successRate;
            return this;
        }

        public Builder totalAmount(double totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public PaymentMethodDTO build() {
            PaymentMethodDTO dto = new PaymentMethodDTO();
            dto.method = this.method;
            dto.successCount = this.successCount;
            dto.failureCount = this.failureCount;
            dto.successRate = this.successRate;
            dto.totalAmount = this.totalAmount;
            return dto;
        }
    }

    // Getters (needed for Jackson serialization)
    public String getMethod() { return method; }
    public long getSuccessCount() { return successCount; }
    public long getFailureCount() { return failureCount; }
    public double getSuccessRate() { return successRate; }
    public double getTotalAmount() { return totalAmount; }
}
