package com.team10.uber.ride.repository.neo4j;

import com.team10.uber.ride.model.neo4j.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {

    Optional<UserNode> findByUserId(Long userId);

    @Query("MATCH (u:User {userId: $userId})-[r:RODE_WITH]->(d:Driver {driverId: $driverId}) RETURN u, r, d")
    Optional<UserNode> findUserWithRelationshipToDriver(@Param("userId") Long userId,
                                                        @Param("driverId") Long driverId);
}