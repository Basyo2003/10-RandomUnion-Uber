package com.team10.uber.payment.strategy;

import com.team10.uber.payment.model.Payment;

// §3.2 — returns full payment amount when refundSurge=true and within 24-hour window
public class FullRefundWithSurgeStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(Payment payment, RefundRequest request) {
        return new RefundResult(
                payment.getAmount(),
                "FULL_REFUND_WITH_SURGE",
                "FullRefundWithSurgeStrategy"
        );
    }
}