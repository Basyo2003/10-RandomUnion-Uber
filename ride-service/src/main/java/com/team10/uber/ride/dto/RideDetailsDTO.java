package com.team10.uber.ride.dto;

import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.enums.StopStatus;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.model.RideStop;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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

    public RideDetailsDTO(Ride ride, List<RideStop> stops) {
        this.id             = ride.getId();
        this.userId         = ride.getUserId();
        this.driverId       = ride.getDriverId();
        this.status         = ride.getStatus();
        this.fare           = ride.getFare();
        this.metadata       = ride.getMetadata() != null ? ride.getMetadata() : new HashMap<>();
        this.stops          = stops;
        this.totalStops     = (long) stops.size();
        this.completedStops = stops.stream()
                .filter(s -> s.getStatus() == StopStatus.REACHED)
                .count();
    }

    public Long getId()                  { return id; }
    public Long getUserId()              { return userId; }
    public Long getDriverId()            { return driverId; }
    public RideStatus getStatus()        { return status; }
    public Double getFare()              { return fare; }
    public Map<String, Object> getMetadata() { return metadata; }
    public List<RideStop> getStops()     { return stops; }
    public Long getTotalStops()          { return totalStops; }
    public Long getCompletedStops()      { return completedStops; }
}