package com.team10.uber.payment.adapter;

import com.team10.uber.payment.dto.RevenueReportDTO;

// §3.8 — Adapter: Object[] SQL result → RevenueReportDTO
public class RevenueReportAdapter {

    public RevenueReportDTO adapt(Object[] revenueRow, Object[] refundRow) {
        // Unwrap nested array if needed
        if (revenueRow != null && revenueRow.length > 0
                && revenueRow[0] instanceof Object[]) {
            revenueRow = (Object[]) revenueRow[0];
        }
        if (refundRow != null && refundRow.length > 0
                && refundRow[0] instanceof Object[]) {
            refundRow = (Object[]) refundRow[0];
        }

        // revenueRow[0] = totalRevenue (Double)
        // revenueRow[1] = totalTransactions (Long)
        // refundRow[0]  = refundedAmount (Double)
        // refundRow[1]  = refundCount (Long)
        Double totalRevenue      = extractDouble(revenueRow, 0, 0.0);
        Long totalTransactions   = extractLong(revenueRow, 1, 0L);
        Double averagePayment    = totalTransactions > 0
                ? totalRevenue / totalTransactions : 0.0;
        Double refundedAmount    = extractDouble(refundRow, 0, 0.0);
        Long refundCount         = extractLong(refundRow, 1, 0L);

        // §3.5 — Use Builder to construct DTO
        return RevenueReportDTO.builder()
                .totalRevenue(totalRevenue)
                .totalTransactions(totalTransactions)
                .averagePayment(averagePayment)
                .refundedAmount(refundedAmount)
                .refundCount(refundCount)
                .build();
    }

    private Double extractDouble(Object[] row, int index, Double defaultVal) {
        if (row == null || row.length <= index || row[index] == null)
            return defaultVal;
        return ((Number) row[index]).doubleValue();
    }

    private Long extractLong(Object[] row, int index, Long defaultVal) {
        if (row == null || row.length <= index || row[index] == null)
            return defaultVal;
        return ((Number) row[index]).longValue();
    }
}   