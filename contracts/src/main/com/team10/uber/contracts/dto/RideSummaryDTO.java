package com.team10.uber.contracts.dto;

public record RideSummaryDTO(
    Long totalRides,
    Long completedRides,
    Long cancelledRides,
    Double totalSpent,
    Double averageFare
) {
    public static RideSummaryDTO empty() {
        return new RideSummaryDTO(0L, 0L, 0L, 0.0, 0.0);
    }
}