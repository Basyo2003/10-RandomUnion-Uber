package com.team10.uber.user.repository.mongo;


import org.springframework.data.mongodb.repository.MongoRepository;
import com.team10.uber.user.event.AuthEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {
}