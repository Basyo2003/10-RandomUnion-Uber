package com.team10.uber.payment.strategy;

import com.team10.uber.payment.model.Payment;

// §3.2 — returns zero when payment is older than 24 hours
public class NoRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(Payment payment, RefundRequest request) {
        return new RefundResult(
                0.0,
                "refund window expired",
                "NoRefundStrategy"
        );
    }
}