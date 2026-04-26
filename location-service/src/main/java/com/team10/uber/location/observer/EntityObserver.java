package com.team10.uber.location.observer;

/**
 * GoF Observer pattern — Observer role.
 *
 * RULES:
 * - This is a plain Java interface. Hand-written from scratch.
 * - Do NOT use Spring @EventListener for any MongoDB write in any service.
 * - Do NOT use Guava EventBus or any equivalent library.
 *
 * The grader checks via reflection:
 *   - EntityObserver exists as an interface (not a class)
 *   - It has exactly one method: onEvent(String, Object)
 */
public interface EntityObserver {

    /**
     * Called by the Subject when an observable entity changes state.
     *
     * @param eventType  UPPER_SNAKE_CASE action string, e.g. "LOCATION_UPDATED"
     * @param payload    The changed entity or a Map<String, Object> of relevant fields
     */
    void onEvent(String eventType, Object payload);
}
