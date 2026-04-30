package com.team10.uber.ride.model.neo4j;

import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Relationship: (User)-[:RODE_WITH]->(Driver)
 * Tracks how many times a user has ridden with a specific driver.
 * recordedRideIds is used for idempotency — prevents double-counting the same ride.
 */
@RelationshipProperties
public class RodeWith {

    @RelationshipId
    private Long id;

    @TargetNode
    private DriverNode driver;

    @Property("rideCount")
    private Integer rideCount;

    @Property("lastRideDate")
    private LocalDateTime lastRideDate;

    @Property("recordedRideIds")
    private List<Long> recordedRideIds = new ArrayList<>();

    public RodeWith() {}

    public RodeWith(DriverNode driver, Integer rideCount, LocalDateTime lastRideDate) {
        this.driver = driver;
        this.rideCount = rideCount;
        this.lastRideDate = lastRideDate;
    }

    public Long getId() { return id; }

    public DriverNode getDriver() { return driver; }
    public void setDriver(DriverNode driver) { this.driver = driver; }

    public Integer getRideCount() { return rideCount; }
    public void setRideCount(Integer rideCount) { this.rideCount = rideCount; }

    public LocalDateTime getLastRideDate() { return lastRideDate; }
    public void setLastRideDate(LocalDateTime lastRideDate) { this.lastRideDate = lastRideDate; }

    public List<Long> getRecordedRideIds() { return recordedRideIds; }
    public void setRecordedRideIds(List<Long> recordedRideIds) { this.recordedRideIds = recordedRideIds; }
}