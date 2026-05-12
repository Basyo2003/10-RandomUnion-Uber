package com.team10.uber.contracts.events;

public record PaymentInitiatedEvent(
    Long paymentId,
    Long rideId,
    Double amount
) {}