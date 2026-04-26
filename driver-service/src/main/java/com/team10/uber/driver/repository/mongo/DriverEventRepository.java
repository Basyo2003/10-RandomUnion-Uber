package com.team10.uber.driver.repository.mongo;

import com.team10.uber.driver.event.DriverEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DriverEventRepository extends MongoRepository<DriverEvent, String> {
}
