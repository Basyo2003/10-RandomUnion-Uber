package com.team10.uber.driver.dto;

public class DriverEarningsDTO {

    private Long driverId;
    private String name;
    private Long totalRides;
    private Double totalEarnings;
    private Double averageFare;

    public DriverEarningsDTO() {}

    public DriverEarningsDTO(Long driverId, String name, Long totalRides, Double totalEarnings, Double averageFare) {
        this.driverId = driverId;
        this.name = name;
        this.totalRides = totalRides;
        this.totalEarnings = totalEarnings;
        this.averageFare = averageFare;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long driverId;
        private String name;
        private Long totalRides;
        private Double totalEarnings;
        private Double averageFare;

        public Builder driverId(Long driverId) { this.driverId = driverId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder totalRides(Long totalRides) { this.totalRides = totalRides; return this; }
        public Builder totalEarnings(Double totalEarnings) { this.totalEarnings = totalEarnings; return this; }
        public Builder averageFare(Double averageFare) { this.averageFare = averageFare; return this; }

        public DriverEarningsDTO build() {
            return new DriverEarningsDTO(driverId, name, totalRides, totalEarnings, averageFare);
        }
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTotalRides() { return totalRides; }
    public void setTotalRides(Long totalRides) { this.totalRides = totalRides; }
    public Double getTotalEarnings() { return totalEarnings; }
    public void setTotalEarnings(Double totalEarnings) { this.totalEarnings = totalEarnings; }
    public Double getAverageFare() { return averageFare; }
    public void setAverageFare(Double averageFare) { this.averageFare = averageFare; }
}