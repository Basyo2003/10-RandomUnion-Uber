package com.team10.uber.ride.repository.mongo;

import com.team10.uber.ride.event.RideEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RideEventRepository extends MongoRepository<RideEvent, String> {
}
