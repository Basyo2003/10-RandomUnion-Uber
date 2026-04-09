package com.team10.uber.ride.dto;

public class RideAnalyticsDTO {

    private long totalRides;
    private long completedRides;
    private long cancelledRides;
    private double totalRevenue;
    private double averageFare;
    private double completionRate;

    public RideAnalyticsDTO() {
    }

    public RideAnalyticsDTO(long totalRides, long completedRides, long cancelledRides,
                            double totalRevenue, double averageFare, double completionRate) {
        this.totalRides = totalRides;
        this.completedRides = completedRides;
        this.cancelledRides = cancelledRides;
        this.totalRevenue = totalRevenue;
        this.averageFare = averageFare;
        this.completionRate = completionRate;
    }

    public long getTotalRides() {
        return totalRides;
    }

    public void setTotalRides(long totalRides) {
        this.totalRides = totalRides;
    }

    public long getCompletedRides() {
        return completedRides;
    }

    public void setCompletedRides(long completedRides) {
        this.completedRides = completedRides;
    }

    public long getCancelledRides() {
        return cancelledRides;
    }

    public void setCancelledRides(long cancelledRides) {
        this.cancelledRides = cancelledRides;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public double getAverageFare() {
        return averageFare;
    }

    public void setAverageFare(double averageFare) {
        this.averageFare = averageFare;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }
}
