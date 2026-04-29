package com.team10.uber.location.dto;

import java.time.LocalDateTime;

public class DriverMovementSummaryDTO {

    private Long driverId;
    private Long totalLocationPoints;
    private Double averageSpeed;
    private Double maxSpeed;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    public DriverMovementSummaryDTO() {}

    public DriverMovementSummaryDTO(Long driverId, Long totalLocationPoints, Double averageSpeed,
                                    Double maxSpeed, LocalDateTime firstTimestamp, LocalDateTime lastTimestamp) {
        this.driverId = driverId;
        this.totalLocationPoints = totalLocationPoints;
        this.averageSpeed = averageSpeed;
        this.maxSpeed = maxSpeed;
        this.firstTimestamp = firstTimestamp;
        this.lastTimestamp = lastTimestamp;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long driverId;
        private Long totalLocationPoints;
        private Double averageSpeed;
        private Double maxSpeed;
        private LocalDateTime firstTimestamp;
        private LocalDateTime lastTimestamp;

        public Builder driverId(Long driverId) { this.driverId = driverId; return this; }
        public Builder totalLocationPoints(Long totalLocationPoints) { this.totalLocationPoints = totalLocationPoints; return this; }
        public Builder averageSpeed(Double averageSpeed) { this.averageSpeed = averageSpeed; return this; }
        public Builder maxSpeed(Double maxSpeed) { this.maxSpeed = maxSpeed; return this; }
        public Builder firstTimestamp(LocalDateTime firstTimestamp) { this.firstTimestamp = firstTimestamp; return this; }
        public Builder lastTimestamp(LocalDateTime lastTimestamp) { this.lastTimestamp = lastTimestamp; return this; }

        public DriverMovementSummaryDTO build() {
            return new DriverMovementSummaryDTO(driverId, totalLocationPoints, averageSpeed, maxSpeed, firstTimestamp, lastTimestamp);
        }
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public Long getTotalLocationPoints() { return totalLocationPoints; }
    public void setTotalLocationPoints(Long totalLocationPoints) { this.totalLocationPoints = totalLocationPoints; }
    public Double getAverageSpeed() { return averageSpeed; }
    public void setAverageSpeed(Double averageSpeed) { this.averageSpeed = averageSpeed; }
    public Double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(Double maxSpeed) { this.maxSpeed = maxSpeed; }
    public LocalDateTime getFirstTimestamp() { return firstTimestamp; }
    public void setFirstTimestamp(LocalDateTime firstTimestamp) { this.firstTimestamp = firstTimestamp; }
    public LocalDateTime getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(LocalDateTime lastTimestamp) { this.lastTimestamp = lastTimestamp; }
}