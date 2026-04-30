package com.team10.uber.location.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("location_tracking_events")
public class LocationTrackingEvent {

    @PrimaryKey
    private LocationTrackingEventKey key;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("speed")
    private Double speed;

    @Column("heading")
    private Double heading;

    @Column("accuracy")
    private Double accuracy;

    @Column("ride_id")
    private Long rideId;

    @Column("notes")
    private String notes;

    public LocationTrackingEvent() {
    }

    public LocationTrackingEvent(
            LocationTrackingEventKey key,
            Double latitude,
            Double longitude,
            Double speed,
            Double heading,
            Double accuracy,
            Long rideId,
            String notes
    ) {
        this.key = key;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.heading = heading;
        this.accuracy = accuracy;
        this.rideId = rideId;
        this.notes = notes;
    }

    public LocationTrackingEventKey getKey() {
        return key;
    }

    public void setKey(LocationTrackingEventKey key) {
        this.key = key;
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

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getHeading() {
        return heading;
    }

    public void setHeading(Double heading) {
        this.heading = heading;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public Long getRideId() {
        return rideId;
    }

    public void setRideId(Long rideId) {
        this.rideId = rideId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}