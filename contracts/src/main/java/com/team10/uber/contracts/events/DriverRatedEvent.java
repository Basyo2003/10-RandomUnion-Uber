package com.team10.uber.contracts.events;

public record DriverRatedEvent(
    Long driverId,
    Long rideId,
    Double rating,
    Long userId
) {}