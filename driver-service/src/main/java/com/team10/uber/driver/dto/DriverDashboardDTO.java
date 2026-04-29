package com.team10.uber.driver.dto;

public class DriverDashboardDTO {

    private Long driverId;
    private String name;
    private Long totalRides;
    private Double totalEarnings;
    private Double averageRideFare;
    private Double averageRating;
    private Integer totalRatings;

    public DriverDashboardDTO() {}

    private DriverDashboardDTO(Builder builder) {
        this.driverId = builder.driverId;
        this.name = builder.name;
        this.totalRides = builder.totalRides;
        this.totalEarnings = builder.totalEarnings;
        this.averageRideFare = builder.averageRideFare;
        this.averageRating = builder.averageRating;
        this.totalRatings = builder.totalRatings;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long driverId;
        private String name;
        private Long totalRides;
        private Double totalEarnings;
        private Double averageRideFare;
        private Double averageRating;
        private Integer totalRatings;

        public Builder driverId(Long driverId)           { this.driverId = driverId; return this; }
        public Builder name(String name)                 { this.name = name; return this; }
        public Builder totalRides(Long totalRides)       { this.totalRides = totalRides; return this; }
        public Builder totalEarnings(Double v)           { this.totalEarnings = v; return this; }
        public Builder averageRideFare(Double v)         { this.averageRideFare = v; return this; }
        public Builder averageRating(Double v)           { this.averageRating = v; return this; }
        public Builder totalRatings(Integer totalRatings){ this.totalRatings = totalRatings; return this; }

        public DriverDashboardDTO build() {
            return new DriverDashboardDTO(this);
        }
    }

    public Long getDriverId()        { return driverId; }
    public void setDriverId(Long v)  { this.driverId = v; }
    public String getName()          { return name; }
    public void setName(String v)    { this.name = v; }
    public Long getTotalRides()      { return totalRides; }
    public void setTotalRides(Long v){ this.totalRides = v; }
    public Double getTotalEarnings() { return totalEarnings; }
    public void setTotalEarnings(Double v) { this.totalEarnings = v; }
    public Double getAverageRideFare()     { return averageRideFare; }
    public void setAverageRideFare(Double v){ this.averageRideFare = v; }
    public Double getAverageRating()       { return averageRating; }
    public void setAverageRating(Double v) { this.averageRating = v; }
    public Integer getTotalRatings()       { return totalRatings; }
    public void setTotalRatings(Integer v) { this.totalRatings = v; }
}
