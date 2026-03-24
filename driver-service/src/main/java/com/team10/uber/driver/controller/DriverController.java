package com.team10.uber.driver.controller;

import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.service.DriverService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    public Driver createDriver(@RequestBody Driver driver) {
        return driverService.createDriver(driver);
    }

    @GetMapping
    public List<Driver> getAllDrivers() {
        return driverService.getAllDrivers();
    }

    @GetMapping("/{id}")
    public Driver getDriverById(@PathVariable Long id) {
        return driverService.getDriverById(id);
    }

    @PutMapping("/{id}")
    public Driver updateDriver(@PathVariable Long id, @RequestBody Driver updatedDriver) {
        return driverService.updateDriver(id, updatedDriver);
    }

    @DeleteMapping("/{id}")
    public String deleteDriver(@PathVariable Long id) {
        driverService.deleteDriver(id);
        return "Driver deleted successfully";
    }
}
