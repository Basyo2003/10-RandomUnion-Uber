package com.team10.uber.user.dto;

public class UserRideSummaryDTO {

    private Long userId;
    private String name;
    private Long totalRides;
    private Long completedRides;
    private Long cancelledRides;
    private Double totalSpent;
    private Double averageFare;

    public UserRideSummaryDTO() {}

    public UserRideSummaryDTO(Long userId, String name, Long totalRides,
                              Long completedRides, Long cancelledRides,
                              Double totalSpent, Double averageFare) {
        this.userId = userId;
        this.name = name;
        this.totalRides = totalRides;
        this.completedRides = completedRides;
        this.cancelledRides = cancelledRides;
        this.totalSpent = totalSpent;
        this.averageFare = averageFare;
    }

    // §3.5 — Static builder() method
    public static Builder builder() { return new Builder(); }

    // §3.5 — Static inner Builder class
    public static class Builder {
        private Long userId;
        private String name;
        private Long totalRides;
        private Long completedRides;
        private Long cancelledRides;
        private Double totalSpent;
        private Double averageFare;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder totalRides(Long totalRides) { this.totalRides = totalRides; return this; }
        public Builder completedRides(Long completedRides) { this.completedRides = completedRides; return this; }
        public Builder cancelledRides(Long cancelledRides) { this.cancelledRides = cancelledRides; return this; }
        public Builder totalSpent(Double totalSpent) { this.totalSpent = totalSpent; return this; }
        public Builder averageFare(Double averageFare) { this.averageFare = averageFare; return this; }

        public UserRideSummaryDTO build() {
            return new UserRideSummaryDTO(userId, name, totalRides, completedRides, cancelledRides, totalSpent, averageFare);
        }
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTotalRides() { return totalRides; }
    public void setTotalRides(Long totalRides) { this.totalRides = totalRides; }
    public Long getCompletedRides() { return completedRides; }
    public void setCompletedRides(Long completedRides) { this.completedRides = completedRides; }
    public Long getCancelledRides() { return cancelledRides; }
    public void setCancelledRides(Long cancelledRides) { this.cancelledRides = cancelledRides; }
    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public Double getAverageFare() { return averageFare; }
    public void setAverageFare(Double averageFare) { this.averageFare = averageFare; }
}