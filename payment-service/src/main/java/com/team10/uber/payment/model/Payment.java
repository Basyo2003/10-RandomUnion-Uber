package com.team10.uber.payment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.team10.uber.payment.enums.PaymentMethod;
import com.team10.uber.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long rideId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private PaymentStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> transactionDetails;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<PaymentCoupon> paymentCoupons;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRideId() { return rideId; }
    public void setRideId(Long rideId) { this.rideId = rideId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
    public void setTransactionDetails(Map<String, Object> transactionDetails) { this.transactionDetails = transactionDetails; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<PaymentCoupon> getPaymentCoupons() { return paymentCoupons; }
    public void setPaymentCoupons(List<PaymentCoupon> paymentCoupons) { this.paymentCoupons = paymentCoupons; }
}