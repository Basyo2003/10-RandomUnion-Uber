package com.team10.uber.driver.repository.mongo;

import com.team10.uber.driver.event.DriverEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverEventRepository extends MongoRepository<DriverEvent, String> {
}
