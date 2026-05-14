package com.team10.uber.ride.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;


public class RideAnalyticsDashboardDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long totalRides;
    private double totalRevenue;
    private double averageRideFare;
    private double completionRate;
    private Map<String, Long> ridesByStatus;

    public RideAnalyticsDashboardDTO() {
        this.ridesByStatus = new LinkedHashMap<>();
    }

    public RideAnalyticsDashboardDTO(long totalRides,
                                     double totalRevenue,
                                     double averageRideFare,
                                     double completionRate,
                                     Map<String, Long> ridesByStatus) {
        this.totalRides = totalRides;
        this.totalRevenue = totalRevenue;
        this.averageRideFare = averageRideFare;
        this.completionRate = completionRate;
        this.ridesByStatus = ridesByStatus != null ? ridesByStatus : new LinkedHashMap<>();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long totalRides;
        private double totalRevenue;
        private double averageRideFare;
        private double completionRate;
        private Map<String, Long> ridesByStatus = new LinkedHashMap<>();

        public Builder totalRides(long totalRides) { this.totalRides = totalRides; return this; }
        public Builder totalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder averageRideFare(double averageRideFare) { this.averageRideFare = averageRideFare; return this; }
        public Builder completionRate(double completionRate) { this.completionRate = completionRate; return this; }
        public Builder ridesByStatus(Map<String, Long> ridesByStatus) {
            this.ridesByStatus = ridesByStatus != null ? ridesByStatus : new LinkedHashMap<>();
            return this;
        }

        public RideAnalyticsDashboardDTO build() {
            return new RideAnalyticsDashboardDTO(totalRides, totalRevenue, averageRideFare, completionRate, ridesByStatus);
        }
    }

    public long getTotalRides() { return totalRides; }
    public void setTotalRides(long totalRides) { this.totalRides = totalRides; }

    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

    public double getAverageRideFare() { return averageRideFare; }
    public void setAverageRideFare(double averageRideFare) { this.averageRideFare = averageRideFare; }

    public double getCompletionRate() { return completionRate; }
    public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }

    public Map<String, Long> getRidesByStatus() { return ridesByStatus; }
    public void setRidesByStatus(Map<String, Long> ridesByStatus) {
        this.ridesByStatus = ridesByStatus != null ? ridesByStatus : new LinkedHashMap<>();
    }
}