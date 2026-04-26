package com.team10.uber.user.repository.mongo;

import com.team10.uber.user.event.AuthEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {
}
