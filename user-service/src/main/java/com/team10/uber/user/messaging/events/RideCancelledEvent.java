package com.team10.uber.user.messaging.events;

public record RideCancelledEvent(Long rideId, Long userId, Long driverId, String reason) {
}
