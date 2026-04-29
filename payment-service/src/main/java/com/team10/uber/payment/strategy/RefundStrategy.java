package com.team10.uber.payment.strategy;

import com.team10.uber.payment.model.Payment;

// §3.2a — interface with exactly one abstract method calculateRefund
public interface RefundStrategy {
    RefundResult calculateRefund(Payment payment, RefundRequest request);
}