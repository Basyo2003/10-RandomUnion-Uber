package com.team10.uber.location.service;

import com.team10.uber.location.event.LocationEvent;
import com.team10.uber.location.event.LocationEventRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class LocationEventService {

    private final LocationEventRepository repository;

    public LocationEventService(LocationEventRepository repository) {
        this.repository = repository;
    }

    public void logEvent(Long driverId, String action, Map<String, Object> details) {
        LocationEvent event = new LocationEvent(driverId, action, details);
        repository.save(event);
    }
}