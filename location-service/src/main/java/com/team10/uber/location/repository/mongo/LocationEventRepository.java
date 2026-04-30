package com.team10.uber.location.repository.mongo;

import com.team10.uber.location.event.LocationEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationEventRepository extends MongoRepository<LocationEvent, String> {

    List<LocationEvent> findByDriverId(Long driverId);

    List<LocationEvent> findByAction(String action);

}