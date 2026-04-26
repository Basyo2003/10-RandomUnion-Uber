package com.team10.uber.payment.repository.mongo;

import com.team10.uber.payment.event.PaymentAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentAuditEventRepository extends MongoRepository<PaymentAuditEvent, String> {
}
