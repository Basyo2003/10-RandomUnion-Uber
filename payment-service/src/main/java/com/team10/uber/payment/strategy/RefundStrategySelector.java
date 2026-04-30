package com.team10.uber.payment.strategy;

import com.team10.uber.payment.model.Payment;

import java.time.LocalDateTime;

// §3.2c — selector chooses which strategy based on refundSurge and payment age
// §3.2g — service must NOT contain if/else branching — all logic is here
public class RefundStrategySelector {

    // §3.2 — 24-hour refund window
    private static final long REFUND_WINDOW_HOURS = 24;

    public RefundStrategy select(Payment payment, RefundRequest request) {
        boolean withinWindow = payment.getCreatedAt()
                .isAfter(LocalDateTime.now().minusHours(REFUND_WINDOW_HOURS));

        if (!withinWindow) {
            return new NoRefundStrategy();
        }

        if (request.isRefundSurge()) {
            return new FullRefundWithSurgeStrategy();
        }

        return new BaseFareOnlyRefundStrategy();
    }
}