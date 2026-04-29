package com.team10.uber.ride.dto;

public class FareEstimateDTO {

    private Double estimatedDistance;
    private Double estimatedDuration;
    private Double estimatedFare;
    private Double surgeMultiplier;

    public FareEstimateDTO() {}

    public FareEstimateDTO(Double estimatedDistance, Double estimatedDuration,
                           Double estimatedFare, Double surgeMultiplier) {
        this.estimatedDistance = estimatedDistance;
        this.estimatedDuration = estimatedDuration;
        this.estimatedFare = estimatedFare;
        this.surgeMultiplier = surgeMultiplier;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Double estimatedDistance;
        private Double estimatedDuration;
        private Double estimatedFare;
        private Double surgeMultiplier;

        public Builder estimatedDistance(Double estimatedDistance) { this.estimatedDistance = estimatedDistance; return this; }
        public Builder estimatedDuration(Double estimatedDuration) { this.estimatedDuration = estimatedDuration; return this; }
        public Builder estimatedFare(Double estimatedFare) { this.estimatedFare = estimatedFare; return this; }
        public Builder surgeMultiplier(Double surgeMultiplier) { this.surgeMultiplier = surgeMultiplier; return this; }

        public FareEstimateDTO build() {
            return new FareEstimateDTO(estimatedDistance, estimatedDuration, estimatedFare, surgeMultiplier);
        }
    }

    public Double getEstimatedDistance() { return estimatedDistance; }
    public Double getEstimatedDuration() { return estimatedDuration; }
    public Double getEstimatedFare() { return estimatedFare; }
    public Double getSurgeMultiplier() { return surgeMultiplier; }
}