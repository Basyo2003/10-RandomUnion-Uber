package com.team10.uber.contracts.events;

public record RidePlacedEvent(
    Long rideId,
    Long userId,
    Long driverId
) {}