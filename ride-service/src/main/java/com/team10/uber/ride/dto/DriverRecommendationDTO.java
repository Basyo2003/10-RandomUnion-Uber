package com.team10.uber.ride.dto;

public class DriverRecommendationDTO {
    private Long driverId;
    private String name;
    private String vehicleType;
    private Long score;

    public DriverRecommendationDTO() {}

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }
}
