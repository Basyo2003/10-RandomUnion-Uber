package com.team10.uber.user.repository.mongo;

import com.team10.uber.user.event.AuthEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {

    Page<AuthEvent> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
}
