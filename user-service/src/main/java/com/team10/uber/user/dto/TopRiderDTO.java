package com.team10.uber.user.dto;

public class TopRiderDTO {

    private Long userId;
    private String name;
    private Double totalSpent;
    private Long rideCount;

    public TopRiderDTO() {}

    public TopRiderDTO(Long userId, String name, Double totalSpent, Long rideCount) {
        this.userId = userId;
        this.name = name;
        this.totalSpent = totalSpent;
        this.rideCount = rideCount;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long userId;
        private String name;
        private Double totalSpent;
        private Long rideCount;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder totalSpent(Double totalSpent) { this.totalSpent = totalSpent; return this; }
        public Builder rideCount(Long rideCount) { this.rideCount = rideCount; return this; }

        public TopRiderDTO build() {
            return new TopRiderDTO(userId, name, totalSpent, rideCount);
        }
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