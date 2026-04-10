package com.team10.uber.location.dto;

import java.util.List;
import java.util.Map;

public class BatchLocationRequest {

    private Long driverId;
    private List<LocationInput> locations;

    public BatchLocationRequest() {
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public List<LocationInput> getLocations() {
        return locations;
    }

    public void setLocations(List<LocationInput> locations) {
        this.locations = locations;
    }

    public static class LocationInput {
        private Double latitude;
        private Double longitude;
        private Map<String, Object> metadata;

        public LocationInput() {
        }

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
}