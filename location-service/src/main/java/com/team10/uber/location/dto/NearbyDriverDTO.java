package com.team10.uber.location.dto;

public class NearbyDriverDTO {

    private Long driverId;
    private String driverName;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;

    public NearbyDriverDTO() {}

    public NearbyDriverDTO(Long driverId, String driverName, Double latitude,
                           Double longitude, Double distanceKm) {
        this.driverId = driverId;
        this.driverName = driverName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long driverId;
        private String driverName;
        private Double latitude;
        private Double longitude;
        private Double distanceKm;

        public Builder driverId(Long driverId) { this.driverId = driverId; return this; }
        public Builder driverName(String driverName) { this.driverName = driverName; return this; }
        public Builder latitude(Double latitude) { this.latitude = latitude; return this; }
        public Builder longitude(Double longitude) { this.longitude = longitude; return this; }
        public Builder distanceKm(Double distanceKm) { this.distanceKm = distanceKm; return this; }

        public NearbyDriverDTO build() {
            return new NearbyDriverDTO(driverId, driverName, latitude, longitude, distanceKm);
        }
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
}