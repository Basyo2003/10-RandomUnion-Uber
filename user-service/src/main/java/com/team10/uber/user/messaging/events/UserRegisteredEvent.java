package com.team10.uber.user.messaging.events;

public record UserRegisteredEvent(Long userId, String email, String role) {
}
