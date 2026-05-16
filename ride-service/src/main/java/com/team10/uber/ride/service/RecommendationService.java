package com.team10.uber.ride.service;

import com.team10.uber.contracts.dto.DriverDTO;
import com.team10.uber.contracts.dto.UserDTO;
import com.team10.uber.contracts.feign.DriverServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.ride.dto.DriverRecommendationDTO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final Neo4jClient neo4jClient;
    private final UserServiceClient userServiceClient;
    private final DriverServiceClient driverServiceClient;

    public RecommendationService(Neo4jClient neo4jClient,
                                 UserServiceClient userServiceClient,
                                 DriverServiceClient driverServiceClient) {
        this.neo4jClient = neo4jClient;
        this.userServiceClient = userServiceClient;
        this.driverServiceClient = driverServiceClient;
    }

    // §5 S3-F12 — ownership check + caller-existence via Feign; driver enrichment via Feign; §2.12 cap at 100
    @Cacheable(value = "ride-service::S3-F12", key = "#userId + '_' + #limit")
    public List<DriverRecommendationDTO> getDriverRecommendations(Long userId, int limit) {
        // §2.10 caller-existence rule: Feign to user-service; 404 → throw 404
        try {
            userServiceClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user-service temporarily unavailable");
        }

        // §2.12 cap candidate set at 100 before Feign fan-out
        int cap = Math.min(limit, 100);

        String cypher = """
                MATCH (me:User {userId: $userId})-[:RODE_WITH]->(shared:Driver)<-[:RODE_WITH]-(other:User)
                MATCH (other)-[:RODE_WITH]->(rec:Driver)
                WHERE NOT (me)-[:RODE_WITH]->(rec)
                RETURN rec.driverId AS driverId, count(DISTINCT other) AS score
                ORDER BY score DESC
                LIMIT $limit
                """;

        Collection<Map<String, Object>> rows;
        try {
            rows = neo4jClient.query(cypher)
                    .bind(userId).to("userId")
                    .bind(cap).to("limit")
                    .fetch().all();
        } catch (Exception e) {
            log.warn("Neo4j query failed for userId {}: {}", userId, e.getMessage());
            return List.of();
        }

        // Enrich each candidate with fresh data from driver-service (§2.4 error handling)
        List<DriverRecommendationDTO> recommendations = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long driverId = ((Number) row.get("driverId")).longValue();
            long score = ((Number) row.get("score")).longValue();
            try {
                DriverDTO driver = driverServiceClient.getDriver(driverId);
                String vehicleType = driver.vehicleDetails() != null
                        ? (String) driver.vehicleDetails().getOrDefault("vehicleType", "") : "";
                DriverRecommendationDTO dto = new DriverRecommendationDTO();
                dto.setDriverId(driver.id());
                dto.setName(driver.name());
                dto.setVehicleType(vehicleType);
                dto.setScore(score);
                recommendations.add(dto);
            } catch (FeignException.NotFound e) {
                // driver deleted since Neo4j write — skip silently
            } catch (FeignException e) {
                log.warn("driver-service unavailable for driverId {}: {}", driverId, e.getMessage());
            }
        }
        return recommendations;
    }
}
