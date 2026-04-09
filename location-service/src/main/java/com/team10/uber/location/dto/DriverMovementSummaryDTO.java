package com.team10.uber.location.dto;

import java.time.LocalDateTime;
//S4-F8
public class DriverMovementSummaryDTO {

    private Long driverId;
    private Long totalLocationPoints;
    private Double averageSpeed;
    private Double maxSpeed;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    public DriverMovementSummaryDTO() {
    }

    public DriverMovementSummaryDTO(Long driverId, Long totalLocationPoints, Double averageSpeed,
                                    Double maxSpeed, LocalDateTime firstTimestamp, LocalDateTime lastTimestamp) {
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

    public Long getTotalLocationPoints() {
        return totalLocationPoints;
    }

    public void setTotalLocationPoints(Long totalLocationPoints) {
        this.totalLocationPoints = totalLocationPoints;
    }

    public Double getAverageSpeed() {
        return averageSpeed;
    }

    public void setAverageSpeed(Double averageSpeed) {
        this.averageSpeed = averageSpeed;
    }

    public Double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(Double maxSpeed) {
        this.maxSpeed = maxSpeed;
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