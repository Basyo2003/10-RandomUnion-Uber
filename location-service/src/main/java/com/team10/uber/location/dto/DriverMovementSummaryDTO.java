package com.team10.uber.location.dto;

import java.time.LocalDateTime;

public class DriverMovementSummaryDTO {

    private Long driverId;
    private int totalLocationPoints;
    private double averageSpeed;
    private double maxSpeed;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    public DriverMovementSummaryDTO() {
    }

    public DriverMovementSummaryDTO(Long driverId, int totalLocationPoints, double averageSpeed, double maxSpeed,
                                    LocalDateTime firstTimestamp, LocalDateTime lastTimestamp) {
        this.driverId = driverId;
        this.totalLocationPoints = totalLocationPoints;
        this.averageSpeed = averageSpeed;
        this.maxSpeed = maxSpeed;
        this.firstTimestamp = firstTimestamp;
        this.lastTimestamp = lastTimestamp;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public int getTotalLocationPoints() {
        return totalLocationPoints;
    }

    public void setTotalLocationPoints(int totalLocationPoints) {
        this.totalLocationPoints = totalLocationPoints;
    }

    public double getAverageSpeed() {
        return averageSpeed;
    }

    public void setAverageSpeed(double averageSpeed) {
        this.averageSpeed = averageSpeed;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public LocalDateTime getFirstTimestamp() {
        return firstTimestamp;
    }

    public void setFirstTimestamp(LocalDateTime firstTimestamp) {
        this.firstTimestamp = firstTimestamp;
    }

    public LocalDateTime getLastTimestamp() {
        return lastTimestamp;
    }

    public void setLastTimestamp(LocalDateTime lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }
}