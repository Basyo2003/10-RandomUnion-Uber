package com.team10.uber.ride.dto;

import java.io.Serializable;

public class FareEstimateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Double estimatedDistance;
    private Double estimatedDuration;
    private Double estimatedFare;
    private Double surgeMultiplier;

    public FareEstimateDTO() {
    }

    public FareEstimateDTO(Double estimatedDistance,
                           Double estimatedDuration,
                           Double estimatedFare,
                           Double surgeMultiplier) {
        this.estimatedDistance = estimatedDistance;
        this.estimatedDuration = estimatedDuration;
        this.estimatedFare = estimatedFare;
        this.surgeMultiplier = surgeMultiplier;
    }

    public Double getEstimatedDistance() {
        return estimatedDistance;
    }

    public void setEstimatedDistance(Double estimatedDistance) {
        this.estimatedDistance = estimatedDistance;
    }

    public Double getEstimatedDuration() {
        return estimatedDuration;
    }

    public void setEstimatedDuration(Double estimatedDuration) {
        this.estimatedDuration = estimatedDuration;
    }

    public Double getEstimatedFare() {
        return estimatedFare;
    }

    public void setEstimatedFare(Double estimatedFare) {
        this.estimatedFare = estimatedFare;
    }

    public Double getSurgeMultiplier() {
        return surgeMultiplier;
    }

    public void setSurgeMultiplier(Double surgeMultiplier) {
        this.surgeMultiplier = surgeMultiplier;
    }
}