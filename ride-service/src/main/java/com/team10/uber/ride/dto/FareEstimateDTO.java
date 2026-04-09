package com.team10.uber.ride.dto;

public class FareEstimateDTO {

    private Double estimatedDistance;
    private Double estimatedDuration;
    private Double estimatedFare;
    private Double surgeMultiplier;

    public FareEstimateDTO(Double estimatedDistance, Double estimatedDuration, Double estimatedFare, Double surgeMultiplier) {
        this.estimatedDistance = estimatedDistance;
        this.estimatedDuration = estimatedDuration;
        this.estimatedFare = estimatedFare;
        this.surgeMultiplier = surgeMultiplier;
    }

    public Double getEstimatedDistance() { return estimatedDistance; }
    public Double getEstimatedDuration() { return estimatedDuration; }
    public Double getEstimatedFare() { return estimatedFare; }
    public Double getSurgeMultiplier() { return surgeMultiplier; }
}