package com.team10.uber.driver.dto;

import java.util.List;

import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;

public class DriverDocumentAlertDTO {
    private Long driverId;
    private String driverName;
    private DriverStatus driverStatus;
    private List<DriverDocument> expiredDocuments;
    private int expiredCount;

    public DriverDocumentAlertDTO(Driver driver,
            List<DriverDocument> expiredDocuments) {
        this.driverId = driver.getId();
        this.driverName = driver.getName();
        this.driverStatus = driver.getStatus();
        this.expiredDocuments = expiredDocuments;
        this.expiredCount = expiredDocuments.size();
    }

    public Long getDriverId() {
        return driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public DriverStatus getDriverStatus() {
        return driverStatus;
    }

    public List<DriverDocument> getExpiredDocuments() {
        return expiredDocuments;
    }

    public int getExpiredCount() {
        return expiredCount;
    }

}
