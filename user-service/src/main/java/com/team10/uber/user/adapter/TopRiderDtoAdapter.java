package com.team10.uber.user.adapter;

import com.team10.uber.user.dto.TopRiderDTO;

import java.util.List;
import java.util.stream.Collectors;

// §3.8 — Adapter: Object[] SQL result → TopRiderDTO
public class TopRiderDtoAdapter {

    public List<TopRiderDTO> adaptAll(List<Object[]> rows) {
        return rows.stream()
                .map(this::adapt)
                .collect(Collectors.toList());
    }

    public TopRiderDTO adapt(Object[] row) {
        Long userId      = ((Number) row[0]).longValue();
        String name      = (String) row[1];
        Double totalSpent = ((Number) row[2]).doubleValue();
        Long rideCount   = ((Number) row[3]).longValue();

        // §3.5 — Use Builder to construct DTO
        return TopRiderDTO.builder()
                .userId(userId)
                .name(name)
                .totalSpent(totalSpent)
                .rideCount(rideCount)
                .build();
    }
}