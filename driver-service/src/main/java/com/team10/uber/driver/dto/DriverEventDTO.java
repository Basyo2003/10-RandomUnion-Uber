package com.team10.uber.driver.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class DriverEventDTO {
    private String id;
    private Long driverId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public DriverEventDTO() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}
