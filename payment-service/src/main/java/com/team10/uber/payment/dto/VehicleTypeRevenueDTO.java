package com.team10.uber.payment.dto;

public class VehicleTypeRevenueDTO {

    private String vehicleType;
    private Double baseFareRevenue;
    private Double surgeFeeRevenue;
    private Double totalRevenue;
    private Long rideCount;

    public VehicleTypeRevenueDTO() {}

    public VehicleTypeRevenueDTO(String vehicleType, Double baseFareRevenue, Double surgeFeeRevenue, Double totalRevenue, Long rideCount) {
        this.vehicleType = vehicleType;
        this.baseFareRevenue = baseFareRevenue;
        this.surgeFeeRevenue = surgeFeeRevenue;
        this.totalRevenue = totalRevenue;
        this.rideCount = rideCount;
    }

    // §3.5 — Builder Pattern Required
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String vehicleType;
        private Double baseFareRevenue;
        private Double surgeFeeRevenue;
        private Double totalRevenue;
        private Long rideCount;

        public Builder vehicleType(String vehicleType) { this.vehicleType = vehicleType; return this; }
        public Builder baseFareRevenue(Double baseFareRevenue) { this.baseFareRevenue = baseFareRevenue; return this; }
        public Builder surgeFeeRevenue(Double surgeFeeRevenue) { this.surgeFeeRevenue = surgeFeeRevenue; return this; }
        public Builder totalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder rideCount(Long rideCount) { this.rideCount = rideCount; return this; }

        public VehicleTypeRevenueDTO build() {
            return new VehicleTypeRevenueDTO(vehicleType, baseFareRevenue, surgeFeeRevenue, totalRevenue, rideCount);
        }
    }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public Double getBaseFareRevenue() { return baseFareRevenue; }
    public void setBaseFareRevenue(Double baseFareRevenue) { this.baseFareRevenue = baseFareRevenue; }
    public Double getSurgeFeeRevenue() { return surgeFeeRevenue; }
    public void setSurgeFeeRevenue(Double surgeFeeRevenue) { this.surgeFeeRevenue = surgeFeeRevenue; }
    public Double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }
    public Long getRideCount() { return rideCount; }
    public void setRideCount(Long rideCount) { this.rideCount = rideCount; }
}