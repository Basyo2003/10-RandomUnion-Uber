package com.team10.uber.location.dto;

import java.time.LocalDateTime;

public interface StationaryDriverDTO {
    Long getDriverId();
    String getDriverName();
    Double getLatitude();
    Double getLongitude();
    Double getLastSpeed();
    LocalDateTime getLastUpdated();
}