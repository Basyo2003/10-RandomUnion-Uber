package com.team10.uber.ride.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RideStopServiceObserverTest {

    @Test
    void rideStopService_extendsAbstractObservableService() {
        Class<?> base = com.team10.uber.ride.observer.AbstractObservableService.class;
        assertTrue(base.isAssignableFrom(RideStopService.class),
            "RideStopService must extend AbstractObservableService");
    }

    @Test
    void rideStopService_hasNotifyObserversInSource() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/team10/uber/ride/service/RideStopService.java"));
        assertTrue(source.contains("notifyObservers("),
            "RideStopService must call notifyObservers() on create/update/delete");
    }
}
