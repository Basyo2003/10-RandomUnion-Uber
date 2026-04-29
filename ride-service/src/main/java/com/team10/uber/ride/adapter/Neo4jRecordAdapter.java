package com.team10.uber.ride.adapter;

import com.team10.uber.ride.dto.DriverRecommendationDTO;
import org.neo4j.driver.Record;

public class Neo4jRecordAdapter implements NoSqlAdapter<Record, DriverRecommendationDTO> {
    @Override
    public DriverRecommendationDTO adapt(Record source) {
        DriverRecommendationDTO dto = new DriverRecommendationDTO();
        dto.setDriverId(source.get("driverId").asLong());
        dto.setName(source.get("name").asString(null));
        dto.setVehicleType(source.containsKey("vehicleType") ? source.get("vehicleType").asString(null) : null);
        dto.setScore(source.containsKey("score") ? source.get("score").asLong() : 0L);
        return dto;
    }
}
