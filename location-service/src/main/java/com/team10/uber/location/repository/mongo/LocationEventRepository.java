package com.team10.uber.location.repository.mongo;

import com.team10.uber.location.event.LocationEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationEventRepository extends MongoRepository<LocationEvent, String> {
}
