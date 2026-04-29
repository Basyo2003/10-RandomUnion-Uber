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

    public DriverDocumentAlertDTO() {}

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

    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public void setDriverStatus(DriverStatus driverStatus) { this.driverStatus = driverStatus; }
    public void setExpiredDocuments(List<DriverDocument> expiredDocuments) {
        this.expiredDocuments = expiredDocuments;
        this.expiredCount = expiredDocuments != null ? expiredDocuments.size() : 0;
    }
    public void setExpiredCount(int expiredCount) { this.expiredCount = expiredCount; }
}
