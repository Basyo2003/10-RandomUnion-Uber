package com.team10.uber.location.dto;

import java.time.LocalDateTime;

public class StationaryDriverDTO {

    private Long driverId;
    private String driverName;
    private Double latitude;
    private Double longitude;
    private Double lastSpeed;
    private LocalDateTime lastUpdated;

    public StationaryDriverDTO() {}

    public StationaryDriverDTO(Long driverId, String driverName, Double latitude,
                               Double longitude, Double lastSpeed, LocalDateTime lastUpdated) {
        this.driverId = driverId;
        this.driverName = driverName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.lastSpeed = lastSpeed;
        this.lastUpdated = lastUpdated;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long driverId;
        private String driverName;
        private Double latitude;
        private Double longitude;
        private Double lastSpeed;
        private LocalDateTime lastUpdated;

        public Builder driverId(Long driverId) { this.driverId = driverId; return this; }
        public Builder driverName(String driverName) { this.driverName = driverName; return this; }
        public Builder latitude(Double latitude) { this.latitude = latitude; return this; }
        public Builder longitude(Double longitude) { this.longitude = longitude; return this; }
        public Builder lastSpeed(Double lastSpeed) { this.lastSpeed = lastSpeed; return this; }
        public Builder lastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; return this; }

        public StationaryDriverDTO build() {
            return new StationaryDriverDTO(driverId, driverName, latitude, longitude, lastSpeed, lastUpdated);
        }
    }

    public Long getDriverId() { return driverId; }
    public String getDriverName() { return driverName; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getLastSpeed() { return lastSpeed; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
}