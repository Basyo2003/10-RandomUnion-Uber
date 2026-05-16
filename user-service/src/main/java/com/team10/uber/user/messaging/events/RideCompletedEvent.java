package com.team10.uber.user.messaging.events;

public record RideCompletedEvent(Long rideId, Long userId, Long driverId, Double fare) {
}
