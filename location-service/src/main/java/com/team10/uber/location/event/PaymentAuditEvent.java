package com.team10.uber.location.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "payment_audit_trail")
public class PaymentAuditEvent implements MongoEvent {

    @Id
    private String id;
    private Long paymentId;
    private String action;
    private LocalDateTime timestamp;
    private String method;
    private Double amount;
    private Map<String, Object> details;

    public PaymentAuditEvent() {}

    public PaymentAuditEvent(Long paymentId, String action, LocalDateTime timestamp,
                              String method, Double amount, Map<String, Object> details) {
        this.paymentId = paymentId;
        this.action = action;
        this.timestamp = timestamp;
        this.method = method;
        this.amount = amount;
        this.details = details;
    }

    @Override public String getId() { return id; }
    @Override public LocalDateTime getTimestamp() { return timestamp; }
    @Override public String getAction() { return action; }
    @Override public Map<String, Object> getDetails() { return details; }

    public Long getPaymentId() { return paymentId; }
    public String getMethod() { return method; }
    public Double getAmount() { return amount; }

    public void setId(String id) { this.id = id; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setMethod(String method) { this.method = method; }
    public void setAmount(Double amount) { this.amount = amount; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}