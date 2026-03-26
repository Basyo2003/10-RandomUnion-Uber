package com.team10.uber.driver.dto;

public class RateDriverDTO {
    private Long rideId;
    private Double rating;

    public Long getRideId() {
        return rideId;
    }

    public void setRideId(Long rideId) {
        this.rideId = rideId;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }
}
