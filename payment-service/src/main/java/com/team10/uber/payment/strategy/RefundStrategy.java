package com.team10.uber.payment.strategy;

import com.team10.uber.payment.model.Payment;

public interface RefundStrategy {
    RefundResult calculateRefund(Payment payment, RefundRequest request);
}