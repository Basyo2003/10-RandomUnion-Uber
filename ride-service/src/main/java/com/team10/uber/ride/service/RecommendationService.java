package com.team10.uber.ride.service;

import com.team10.uber.ride.dto.DriverRecommendationDTO;
import com.team10.uber.ride.repository.RideRepository;
import org.neo4j.driver.Record;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public class RecommendationService {

    private final Neo4jClient neo4jClient;
    private final RideRepository rideRepository;

    public RecommendationService(Neo4jClient neo4jClient, RideRepository rideRepository) {
        this.neo4jClient = neo4jClient;
        this.rideRepository = rideRepository;
    }

    @Cacheable(value = "ride-service::S3-F12", key = "#userId + '_' + #limit")
    public List<DriverRecommendationDTO> getDriverRecommendations(Long userId, int limit) {
        if (!rideRepository.userExists(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
        }

        String cypher = """
                MATCH (me:User {userId: $userId})-[:RODE_WITH]->(shared:Driver)<-[:RODE_WITH]-(other:User)
                MATCH (other)-[:RODE_WITH]->(rec:Driver)
                WHERE NOT (me)-[:RODE_WITH]->(rec)
                RETURN rec.driverId AS driverId, rec.name AS name, rec.vehicleType AS vehicleType,
                       count(DISTINCT other) AS score
                ORDER BY score DESC
                LIMIT $limit
                """;

        try {
            Collection<DriverRecommendationDTO> results = neo4jClient.query(cypher)
                    .bind(userId).to("userId")
                    .bind(limit).to("limit")
                    .fetchAs(DriverRecommendationDTO.class)
                    .mappedBy((typeSystem, record) -> {
                        DriverRecommendationDTO dto = new DriverRecommendationDTO();
                        dto.setDriverId(record.get("driverId").asLong());
                        dto.setName(record.get("name").asString(null));
                        dto.setVehicleType(record.containsKey("vehicleType") && !record.get("vehicleType").isNull()
                                ? record.get("vehicleType").asString() : null);
                        dto.setScore(record.get("score").asLong());
                        return dto;
                    })
                    .all();
            return new ArrayList<>(results);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}