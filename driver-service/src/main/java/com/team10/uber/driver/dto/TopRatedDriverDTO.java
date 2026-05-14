package com.team10.uber.driver.dto;

public class TopRatedDriverDTO {

    private Long driverId;
    private String name;
    private Double rating;
    private Long totalRides;

    public TopRatedDriverDTO() {}

    public TopRatedDriverDTO(Long driverId, String name, Double rating, Long totalRides) {
        this.driverId = driverId;
        this.name = name;
        this.rating = rating;
        this.totalRides = totalRides;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long driverId;
        private String name;
        private Double rating;
        private Long totalRides;

        public Builder driverId(Long driverId) { this.driverId = driverId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder rating(Double rating) { this.rating = rating; return this; }
        public Builder totalRides(Long totalRides) { this.totalRides = totalRides; return this; }

        public TopRatedDriverDTO build() {
            return new TopRatedDriverDTO(driverId, name, rating, totalRides);
        }
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Long getTotalRides() { return totalRides; }
    public void setTotalRides(Long totalRides) { this.totalRides = totalRides; }
}