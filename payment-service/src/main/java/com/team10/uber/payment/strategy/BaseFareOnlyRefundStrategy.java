package com.team10.uber.payment.strategy;

import com.team10.uber.payment.model.Payment;

// §3.2 — returns amount minus surge fee when refundSurge=false and within 24-hour window
public class BaseFareOnlyRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(Payment payment, RefundRequest request) {
        // §4.6 — get surgeFee from transactionDetails, default to 15% if missing
        double surgeFee = 0.0;
        if (payment.getTransactionDetails() != null
                && payment.getTransactionDetails().containsKey("surgeFee")) {
            surgeFee = ((Number) payment.getTransactionDetails().get("surgeFee")).doubleValue();
        } else {
            surgeFee = payment.getAmount() * 0.15;
        }

        double refundAmount = payment.getAmount() - surgeFee;

        return new RefundResult(
                refundAmount,
                "BASE_FARE_ONLY_REFUND",
                "BaseFareOnlyRefundStrategy"
        );
    }
}