package com.team10.uber.driver.dto;

import java.util.List;

import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;

public class DriverWithDocumentsDTO {
    private Driver driver;
    private List<DriverDocument> driverDocuments;

    public DriverWithDocumentsDTO(Driver driver, List<DriverDocument> driverDocuments) {
        this.driver = driver;
        this.driverDocuments = driverDocuments;
    }

    public Driver getDriver() {
        return driver;
    }

    public List<DriverDocument> getDriverDocuments() {
        return driverDocuments;
    }
}