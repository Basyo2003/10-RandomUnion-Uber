package com.team10.uber.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_ride_stats_events")
public class UserRideStatsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long rideId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Double fare;

    @Column(nullable = false)
    private Boolean completedApplied = false;

    @Column(nullable = false)
    private Boolean cancelledApplied = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    protected UserRideStatsEvent() {
    }

    public UserRideStatsEvent(Long rideId, Long userId, Double fare) {
        this.rideId = rideId;
        this.userId = userId;
        this.fare = fare;
    }

    public Long getId() { return id; }

    public Long getRideId() { return rideId; }
    public void setRideId(Long rideId) { this.rideId = rideId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getFare() { return fare; }
    public void setFare(Double fare) { this.fare = fare; }

    public Boolean getCompletedApplied() { return completedApplied; }
    public void setCompletedApplied(Boolean completedApplied) { this.completedApplied = completedApplied; }

    public Boolean getCancelledApplied() { return cancelledApplied; }
    public void setCancelledApplied(Boolean cancelledApplied) { this.cancelledApplied = cancelledApplied; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
