package com.team10.uber.location.event;

import com.team10.uber.location.repository.mongo.LocationEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class LocationEventService {

    private final LocationEventRepository locationEventRepository;

    public LocationEventService(LocationEventRepository locationEventRepository) {
        this.locationEventRepository = locationEventRepository;
    }

    public LocationEvent logEvent(Long driverId, String action, Map<String, Object> details) {
        LocationEvent event = new LocationEvent();

        event.setDriverId(driverId);
        event.setAction(action);
        event.setTimestamp(LocalDateTime.now());
        event.setDetails(details != null ? details : new HashMap<>());

        return locationEventRepository.save(event);
    }
}