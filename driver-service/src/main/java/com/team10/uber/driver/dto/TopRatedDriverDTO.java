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

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Long getTotalRides() { return totalRides; }
    public void setTotalRides(Long totalRides) { this.totalRides = totalRides; }
}