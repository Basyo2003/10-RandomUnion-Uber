package com.team10.uber.location.dto;

import java.time.LocalDateTime;
//S4-F8
public interface DriverMovementSummaryProjection {
    Long getDriverId();
    Long getTotalLocationPoints();
    Double getAverageSpeed();
    Double getMaxSpeed();
    LocalDateTime getFirstTimestamp();
    LocalDateTime getLastTimestamp();
}