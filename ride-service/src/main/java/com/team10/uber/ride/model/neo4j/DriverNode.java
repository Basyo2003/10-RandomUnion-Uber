package com.team10.uber.ride.model.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node("Driver")
public class DriverNode {

    @Id
    private Long driverId;

    @Property("name")
    private String name;

    @Property("vehicleType")
    private String vehicleType;

    public DriverNode() {}

    public DriverNode(Long driverId, String name, String vehicleType) {
        this.driverId = driverId;
        this.name = name;
        this.vehicleType = vehicleType;
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
}
