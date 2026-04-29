package com.team10.uber.location.controller;

import com.team10.uber.location.event.LocationEvent;
import com.team10.uber.location.event.LocationEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events")
public class LocationEventController {

    private final LocationEventRepository repository;

    public LocationEventController(LocationEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<LocationEvent> getAll() {
        return repository.findAll();
    }
}