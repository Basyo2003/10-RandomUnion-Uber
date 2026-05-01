package com.team10.uber.payment;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyPatternReflectionTest {

    @Test
    void refundStrategy_isInterface_withCalculateRefund() throws Exception {
        Class<?> strategyClass = Class.forName("com.team10.uber.payment.strategy.RefundStrategy");
        assertTrue(strategyClass.isInterface(), "RefundStrategy must be an interface");

        Class<?> paymentClass = Class.forName("com.team10.uber.payment.model.Payment");
        Class<?> refundRequestClass = Class.forName("com.team10.uber.payment.strategy.RefundRequest");

        Method calculateRefund = strategyClass.getMethod("calculateRefund", paymentClass, refundRequestClass);
        assertNotNull(calculateRefund, "RefundStrategy must have calculateRefund(Payment, RefundRequest) method");

        long abstractMethodCount = Arrays.stream(strategyClass.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()) && !m.isBridge())
                .count();
        assertEquals(1, abstractMethodCount, "RefundStrategy must have exactly 1 abstract method");
    }

    @Test
    void allThreeConcreteStrategies_implementRefundStrategy() throws Exception {
        Class<?> strategyInterface = Class.forName("com.team10.uber.payment.strategy.RefundStrategy");

        List<String> concreteStrategyNames = List.of(
                "com.team10.uber.payment.strategy.FullRefundWithSurgeStrategy",
                "com.team10.uber.payment.strategy.BaseFareOnlyRefundStrategy",
                "com.team10.uber.payment.strategy.NoRefundStrategy"
        );

        for (String name : concreteStrategyNames) {
            Class<?> concrete = Class.forName(name);
            assertTrue(strategyInterface.isAssignableFrom(concrete),
                    name + " must implement RefundStrategy");
            assertFalse(concrete.isInterface(), name + " must be a concrete class, not an interface");
        }
    }

    @Test
    void refundStrategySelector_exists_withSelectMethod() throws Exception {
        Class<?> selectorClass = Class.forName("com.team10.uber.payment.strategy.RefundStrategySelector");
        assertNotNull(selectorClass, "RefundStrategySelector must exist");

        Class<?> strategyInterface = Class.forName("com.team10.uber.payment.strategy.RefundStrategy");

        boolean hasSelectMethod = Arrays.stream(selectorClass.getMethods())
                .anyMatch(m -> m.getName().startsWith("select")
                        && strategyInterface.isAssignableFrom(m.getReturnType()));

        assertTrue(hasSelectMethod, "RefundStrategySelector must have a select*() method returning RefundStrategy");
    }
}
