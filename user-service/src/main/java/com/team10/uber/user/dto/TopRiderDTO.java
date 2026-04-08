package com.team10.uber.user.dto;

public class TopRiderDTO {

    private Long userId;
    private String name;
    private Double totalSpent;
    private Long rideCount;

    public TopRiderDTO(Long userId, String name, Double totalSpent, Long rideCount) {
        this.userId = userId;
        this.name = name;
        this.totalSpent = totalSpent;
        this.rideCount = rideCount;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }

    public Long getRideCount() { return rideCount; }
    public void setRideCount(Long rideCount) { this.rideCount = rideCount; }
}
