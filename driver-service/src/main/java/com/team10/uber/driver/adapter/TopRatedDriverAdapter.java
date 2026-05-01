package com.team10.uber.driver.adapter;

import com.team10.uber.driver.dto.TopRatedDriverDTO;

import java.util.List;
import java.util.stream.Collectors;

// §3.8 — Adapter: Object[] SQL result → TopRatedDriverDTO
public class TopRatedDriverAdapter {

    public List<TopRatedDriverDTO> adaptAll(List<Object[]> rows) {
        return rows.stream()
                .map(this::adapt)
                .collect(Collectors.toList());
    }

    public TopRatedDriverDTO adapt(Object[] row) {
        Long driverId    = ((Number) row[0]).longValue();
        String name      = (String) row[1];
        Double rating    = ((Number) row[2]).doubleValue();
        Long totalRides  = ((Number) row[3]).longValue();

        // §3.5 — Use Builder
        return TopRatedDriverDTO.builder()
                .driverId(driverId)
                .name(name)
                .rating(rating)
                .totalRides(totalRides)
                .build();
    }
}