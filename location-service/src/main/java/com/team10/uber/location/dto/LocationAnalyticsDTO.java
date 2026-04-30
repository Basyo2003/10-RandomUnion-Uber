package com.team10.uber.location.dto;

import java.util.Map;

public class LocationAnalyticsDTO {

    private Long totalLocationEvents;
    private Long activeDrivers;
    private Double averageSpeed;
    private Map<Integer, Long> eventsByHour;

    public LocationAnalyticsDTO() {
    }

    public LocationAnalyticsDTO(
            Long totalLocationEvents,
            Long activeDrivers,
            Double averageSpeed,
            Map<Integer, Long> eventsByHour
    ) {
        this.totalLocationEvents = totalLocationEvents;
        this.activeDrivers = activeDrivers;
        this.averageSpeed = averageSpeed;
        this.eventsByHour = eventsByHour;
    }

    // §3.5 — Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long totalLocationEvents;
        private Long activeDrivers;
        private Double averageSpeed;
        private Map<Integer, Long> eventsByHour;

        public Builder totalLocationEvents(Long totalLocationEvents) {
            this.totalLocationEvents = totalLocationEvents;
            return this;
        }

        public Builder activeDrivers(Long activeDrivers) {
            this.activeDrivers = activeDrivers;
            return this;
        }

        public Builder averageSpeed(Double averageSpeed) {
            this.averageSpeed = averageSpeed;
            return this;
        }

        public Builder eventsByHour(Map<Integer, Long> eventsByHour) {
            this.eventsByHour = eventsByHour;
            return this;
        }

        public LocationAnalyticsDTO build() {
            return new LocationAnalyticsDTO(
                    totalLocationEvents,
                    activeDrivers,
                    averageSpeed,
                    eventsByHour
            );
        }
    }

    public Long getTotalLocationEvents() {
        return totalLocationEvents;
    }

    public void setTotalLocationEvents(Long totalLocationEvents) {
        this.totalLocationEvents = totalLocationEvents;
    }

    public Long getActiveDrivers() {
        return activeDrivers;
    }

    public void setActiveDrivers(Long activeDrivers) {
        this.activeDrivers = activeDrivers;
    }

    public Double getAverageSpeed() {
        return averageSpeed;
    }

    public void setAverageSpeed(Double averageSpeed) {
        this.averageSpeed = averageSpeed;
    }

    public Map<Integer, Long> getEventsByHour() {
        return eventsByHour;
    }

    public void setEventsByHour(Map<Integer, Long> eventsByHour) {
        this.eventsByHour = eventsByHour;
    }
}