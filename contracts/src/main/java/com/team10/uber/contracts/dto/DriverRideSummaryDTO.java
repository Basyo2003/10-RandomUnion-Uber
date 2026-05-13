package com.team10.uber.contracts.dto;

public record DriverRideSummaryDTO(
    Long totalRides,
    Double totalEarnings,
    Double averageFare
) {
    public static DriverRideSummaryDTO empty() {
        return new DriverRideSummaryDTO(0L, 0.0, 0.0);
    }
}