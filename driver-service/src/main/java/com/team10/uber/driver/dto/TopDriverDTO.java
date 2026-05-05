package com.team10.uber.driver.dto;

public class TopDriverDTO {

    private Long driverId;
    private String name;
    private Double rating;
    private Integer totalRides;
    private Double totalEarnings;

    public TopDriverDTO() {}

    public TopDriverDTO(Long driverId, String name, Double rating, Integer totalRides, Double totalEarnings) {
        this.driverId = driverId;
        this.name = name;
        this.rating = rating;
        this.totalRides = totalRides;
        this.totalEarnings = totalEarnings;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long driverId;
        private String name;
        private Double rating;
        private Integer totalRides;
        private Double totalEarnings;

        public Builder driverId(Long driverId) { this.driverId = driverId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder rating(Double rating) { this.rating = rating; return this; }
        public Builder totalRides(Integer totalRides) { this.totalRides = totalRides; return this; }
        public Builder totalEarnings(Double totalEarnings) { this.totalEarnings = totalEarnings; return this; }

        public TopDriverDTO build() {
            return new TopDriverDTO(driverId, name, rating, totalRides, totalEarnings);
        }
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Integer getTotalRides() { return totalRides; }
    public void setTotalRides(Integer totalRides) { this.totalRides = totalRides; }
    public Double getTotalEarnings() { return totalEarnings; }
    public void setTotalEarnings(Double totalEarnings) { this.totalEarnings = totalEarnings; }
}
