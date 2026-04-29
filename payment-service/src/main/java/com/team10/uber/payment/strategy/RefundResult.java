package com.team10.uber.payment.strategy;

public class RefundResult {
    private double amount;
    private String reasonCode;
    private String strategyName;

    public RefundResult(double amount, String reasonCode, String strategyName) {
        this.amount = amount;
        this.reasonCode = reasonCode;
        this.strategyName = strategyName;
    }

    public double getAmount() { return amount; }
    public String getReasonCode() { return reasonCode; }
    public String getStrategyName() { return strategyName; }
}