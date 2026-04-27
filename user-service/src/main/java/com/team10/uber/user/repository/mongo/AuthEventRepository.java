package com.team10.uber.user.repository.mongo;

import com.team10.uber.user.model.mongo.AuthEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {
}