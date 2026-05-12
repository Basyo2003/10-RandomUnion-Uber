package com.team10.uber.contracts.events;

public record PaymentFailedEvent(
    Long paymentId,
    Long rideId,
    String reason
) {}