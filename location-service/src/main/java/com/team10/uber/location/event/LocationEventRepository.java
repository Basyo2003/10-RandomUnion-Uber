package com.team10.uber.location.event;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LocationEventRepository extends MongoRepository<LocationEvent, String> {

    List<LocationEvent> findByDriverId(Long driverId);

    List<LocationEvent> findByAction(String action);
}