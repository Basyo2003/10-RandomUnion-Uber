package com.team10.uber.contracts.events;

public record UserRegisteredEvent(
    Long userId,
    String email,
    String role
) {}