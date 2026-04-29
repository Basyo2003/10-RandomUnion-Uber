package com.team10.uber.ride.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.enums.StopStatus;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.model.RideStop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RideDetailsDTO {

    @JsonProperty("rideId")
    private Long id;
    private Long userId;
    private Long driverId;
    private RideStatus status;
    private Double fare;
    private Map<String, Object> metadata;
    private List<RideStop> stops;
    private Long totalStops;
    private Long completedStops;

    public RideDetailsDTO() {}

    public RideDetailsDTO(Ride ride, List<RideStop> stops) {
        this.id = ride.getId();
        this.userId = ride.getUserId();
        this.driverId = ride.getDriverId();
        this.status = ride.getStatus();
        this.fare = ride.getFare();
        this.metadata = ride.getMetadata() != null ? ride.getMetadata() : new HashMap<>();
        this.stops = stops;
        this.totalStops = (long) stops.size();
        this.completedStops = stops.stream()
                .filter(s -> s.getStatus() == StopStatus.REACHED)
                .count();
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private Long userId;
        private Long driverId;
        private RideStatus status;
        private Double fare;
        private Map<String, Object> metadata;
        private List<RideStop> stops;
        private Long totalStops;
        private Long completedStops;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder driverId(Long driverId) { this.driverId = driverId; return this; }
        public Builder status(RideStatus status) { this.status = status; return this; }
        public Builder fare(Double fare) { this.fare = fare; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder stops(List<RideStop> stops) { this.stops = stops; return this; }
        public Builder totalStops(Long totalStops) { this.totalStops = totalStops; return this; }
        public Builder completedStops(Long completedStops) { this.completedStops = completedStops; return this; }

        public RideDetailsDTO build() {
            RideDetailsDTO dto = new RideDetailsDTO();
            dto.id = this.id;
            dto.userId = this.userId;
            dto.driverId = this.driverId;
            dto.status = this.status;
            dto.fare = this.fare;
            dto.metadata = this.metadata;
            dto.stops = this.stops;
            dto.totalStops = this.totalStops;
            dto.completedStops = this.completedStops;
            return dto;
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getDriverId() { return driverId; }
    public RideStatus getStatus() { return status; }
    public Double getFare() { return fare; }
    public Map<String, Object> getMetadata() { return metadata; }
    public List<RideStop> getStops() { return stops; }
    public Long getTotalStops() { return totalStops; }
    public Long getCompletedStops() { return completedStops; }
}