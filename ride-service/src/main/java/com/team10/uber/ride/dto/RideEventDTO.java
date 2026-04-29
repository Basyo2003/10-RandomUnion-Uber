package com.team10.uber.ride.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class RideEventDTO {
    private String id;
    private Long rideId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public RideEventDTO() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getRideId() { return rideId; }
    public void setRideId(Long rideId) { this.rideId = rideId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}
