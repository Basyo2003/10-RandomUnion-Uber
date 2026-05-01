package com.team10.uber.payment.repository.mongo;

import com.team10.uber.payment.event.PaymentAuditEvent;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

@Repository
public interface PaymentAuditEventRepository extends MongoRepository<PaymentAuditEvent, String> {
    // S5-F11 query - only COMPLETED and FAILED events
    @Query("{ 'timestamp' : { $gte: ?0, $lte: ?1 }, 'action' : { $in: ['COMPLETED', 'FAILED'] } }")
    List<PaymentAuditEvent> findByTimestampBetweenAndActionIn(
            LocalDateTime startDate, LocalDateTime endDate);
}
