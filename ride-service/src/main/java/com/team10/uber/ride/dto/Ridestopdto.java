package com.team10.uber.ride.dto;

import com.team10.uber.ride.enums.RideStopStatus;
import com.team10.uber.ride.model.RideStop;

import java.util.Map;

public class RideStopDTO {

    private Long id;
    private Integer stopOrder;
    private String address;
    private Double latitude;
    private Double longitude;
    private RideStopStatus status;
    private Map<String, Object> metadata;

    public RideStopDTO(RideStop stop) {
        this.id = stop.getId();
        this.stopOrder = stop.getStopOrder();
        this.address = stop.getAddress();
        this.latitude = stop.getLatitude();
        this.longitude = stop.getLongitude();
        this.status = stop.getStatus();
        this.metadata = stop.getMetadata();
    }

    public Long getId() { return id; }
    public Integer getStopOrder() { return stopOrder; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public RideStopStatus getStatus() { return status; }
    public Map<String, Object> getMetadata() { return metadata; }
}
