package com.team10.uber.driver.model.elastic;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "drivers")
public class DriverSearchDocument {

    @Id
    private String id;           // matches Driver.id from PostgreSQL (stored as String in ES)

    @Field(type = FieldType.Text)
    private String name;         // analyzed — full-text searchable

    @Field(type = FieldType.Keyword)
    private String vehicleType;  // exact match only (SEDAN, SUV, HATCHBACK, VAN, LUXURY)

    @Field(type = FieldType.Text)
    private String description;  // analyzed — full-text searchable (from vehicleDetails JSONB)

    @Field(type = FieldType.Double)
    private Double rating;       // range filter

    @Field(type = FieldType.Keyword)
    private String status;       // exact match (AVAILABLE, BUSY, OFFLINE)

    public DriverSearchDocument() {}

    public DriverSearchDocument(String id, String name, String vehicleType,
                                 String description, Double rating, String status) {
        this.id = id;
        this.name = name;
        this.vehicleType = vehicleType;
        this.description = description;
        this.rating = rating;
        this.status = status;
    }

    public String getId()          { return id; }
    public String getName()        { return name; }
    public String getVehicleType() { return vehicleType; }
    public String getDescription() { return description; }
    public Double getRating()      { return rating; }
    public String getStatus()      { return status; }

    public void setId(String id)                   { this.id = id; }
    public void setName(String name)               { this.name = name; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public void setDescription(String description) { this.description = description; }
    public void setRating(Double rating)           { this.rating = rating; }
    public void setStatus(String status)           { this.status = status; }
}
