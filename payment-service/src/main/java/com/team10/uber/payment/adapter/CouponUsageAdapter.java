package com.team10.uber.payment.adapter;

import com.team10.uber.payment.dto.CouponUsageDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

// §3.8 — Adapter: Object[] SQL result → CouponUsageDTO
public class CouponUsageAdapter {

    public List<CouponUsageDTO> adaptAll(List<Object[]> rows) {
        return rows.stream()
                .map(this::adapt)
                .collect(Collectors.toList());
    }

    public CouponUsageDTO adapt(Object[] row) {
        // row[0] = couponId (Long)
        // row[1] = code (String)
        // row[2] = discountType (String)
        // row[3] = discountValue (Double)
        // row[4] = timesUsed (Integer)
        // row[5] = totalDiscountGiven (Double)
        // row[6] = active (Boolean)
        // row[7] = expiryDate (Timestamp or LocalDateTime or String)
        Long couponId        = ((Number) row[0]).longValue();
        String code          = (String) row[1];
        String discountType  = (String) row[2];
        Double discountValue = ((Number) row[3]).doubleValue();
        Integer timesUsed    = ((Number) row[4]).intValue();
        Double totalDiscount = ((Number) row[5]).doubleValue();
        Boolean active       = (Boolean) row[6];

        LocalDateTime expiryDate;
        if (row[7] instanceof java.sql.Timestamp ts) {
            expiryDate = ts.toLocalDateTime();
        } else if (row[7] instanceof LocalDateTime ldt) {
            expiryDate = ldt;
        } else {
            expiryDate = LocalDateTime.parse(row[7].toString());
        }

        Boolean expired = expiryDate.isBefore(LocalDateTime.now());

        // §3.5 — Use Builder to construct DTO
        return CouponUsageDTO.builder()
                .couponId(couponId)
                .code(code)
                .discountType(discountType)
                .discountValue(discountValue)
                .timesUsed(timesUsed)
                .totalDiscountGiven(totalDiscount)
                .active(active)
                .expired(expired)
                .build();
    }
}