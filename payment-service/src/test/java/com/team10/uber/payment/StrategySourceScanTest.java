package com.team10.uber.payment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StrategySourceScanTest {

    @Test
    void paymentService_refundMethod_hasNoIfRefundSurgeBranching() throws Exception {
        Path path = Path.of(
                "src/main/java/com/team10/uber/payment/service/PaymentService.java");
        assertTrue(Files.exists(path), "PaymentService.java must exist at: " + path);

        String source = Files.readString(path);

        assertFalse(source.contains("if (refundSurge)"),
                "PaymentService must NOT contain 'if (refundSurge)' branching — use Strategy pattern instead");

        boolean usesSelector = source.contains("selector.select(") || source.contains("Selector");
        assertTrue(usesSelector,
                "PaymentService must delegate to RefundStrategySelector (selector.select() or Selector reference)");
    }

    @Test
    void paymentService_refundSurgeAdjusted_usesStrategySelector() throws Exception {
        Path path = Path.of(
                "src/main/java/com/team10/uber/payment/service/PaymentService.java");
        String source = Files.readString(path);

        assertTrue(source.contains("RefundStrategySelector"),
                "PaymentService must use RefundStrategySelector");
        assertTrue(source.contains("RefundStrategy"),
                "PaymentService must reference RefundStrategy interface");
    }
}
