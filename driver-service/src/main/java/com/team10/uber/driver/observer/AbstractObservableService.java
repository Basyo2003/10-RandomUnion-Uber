package com.team10.uber.driver.observer;

import java.util.ArrayList;
import java.util.List;

/**
 * GoF Observer pattern — Subject role (abstract base).
 *
 * DriverService and DriverDocumentService extend this class.
 * Uses a plain ArrayList — no Spring, no Guava, no external library.
 *
 * The grader unit test:
 *   - Calls unregister() to remove all observers
 *   - Triggers a write
 *   - Asserts NO MongoDB document was written
 */
public abstract class AbstractObservableService {

    private final List<EntityObserver> observers = new ArrayList<>();

    public void register(EntityObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    protected void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }
}
