package com.team10.uber.payment.adapter;

import com.team10.uber.payment.dto.UserPaymentSummaryDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// §3.8 — Adapter: Object[] SQL result → UserPaymentSummaryDTO
public class PaymentSummaryAdapter {

    public UserPaymentSummaryDTO adapt(Long userId, List<Object[]> rows) {
        Map<String, Double> methodBreakdown = new HashMap<>();
        long totalPayments = 0;
        double totalAmount = 0.0;

        for (Object[] row : rows) {
            // row[0] = method (String)
            // row[1] = count (Long)
            // row[2] = total amount (Double)
            String method = (String) row[0];
            long count    = row[1] == null ? 0L : ((Number) row[1]).longValue();
            double total  = row[2] == null ? 0.0 : ((Number) row[2]).doubleValue();

            methodBreakdown.put(method, total);
            totalPayments += count;
            totalAmount   += total;
        }

        // §3.5 — Use Builder to construct DTO
        return UserPaymentSummaryDTO.builder()
                .userId(userId)
                .totalPayments(totalPayments)
                .totalAmount(totalAmount)
                .methodBreakdown(methodBreakdown)
                .build();
    }
}