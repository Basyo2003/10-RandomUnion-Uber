package com.team10.uber.payment.dto;

public class RevenueReportDTO {

    private Double totalRevenue;
    private Long totalTransactions;
    private Double averagePayment;
    private Double refundedAmount;
    private Long refundCount;

    public RevenueReportDTO() {}

    public RevenueReportDTO(Double totalRevenue, Long totalTransactions, Double averagePayment,
                            Double refundedAmount, Long refundCount) {
        this.totalRevenue = totalRevenue;
        this.totalTransactions = totalTransactions;
        this.averagePayment = averagePayment;
        this.refundedAmount = refundedAmount;
        this.refundCount = refundCount;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Double totalRevenue;
        private Long totalTransactions;
        private Double averagePayment;
        private Double refundedAmount;
        private Long refundCount;

        public Builder totalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder totalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; return this; }
        public Builder averagePayment(Double averagePayment) { this.averagePayment = averagePayment; return this; }
        public Builder refundedAmount(Double refundedAmount) { this.refundedAmount = refundedAmount; return this; }
        public Builder refundCount(Long refundCount) { this.refundCount = refundCount; return this; }

        public RevenueReportDTO build() {
            return new RevenueReportDTO(totalRevenue, totalTransactions, averagePayment, refundedAmount, refundCount);
        }
    }

    public Double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }
    public Long getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; }
    public Double getAveragePayment() { return averagePayment; }
    public void setAveragePayment(Double averagePayment) { this.averagePayment = averagePayment; }
    public Double getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(Double refundedAmount) { this.refundedAmount = refundedAmount; }
    public Long getRefundCount() { return refundCount; }
    public void setRefundCount(Long refundCount) { this.refundCount = refundCount; }
}