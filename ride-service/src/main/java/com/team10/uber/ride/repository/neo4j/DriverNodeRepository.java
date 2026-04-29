package com.team10.uber.ride.repository.neo4j;

import com.team10.uber.ride.model.neo4j.DriverNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverNodeRepository extends Neo4jRepository<DriverNode, Long> {

    Optional<DriverNode> findByDriverId(Long driverId);
}
