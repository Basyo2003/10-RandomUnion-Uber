package com.team10.uber.driver.dto;

import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;

import java.util.List;

public class DriverDocumentAlertDTO {

    private Long driverId;
    private String driverName;
    private DriverStatus driverStatus;
    private List<DriverDocument> expiredDocuments;
    private int expiredCount;

    public DriverDocumentAlertDTO() {}

    public DriverDocumentAlertDTO(Driver driver, List<DriverDocument> expiredDocuments) {
        this.driverId = driver.getId();
        this.driverName = driver.getName();
        this.driverStatus = driver.getStatus();
        this.expiredDocuments = expiredDocuments;
        this.expiredCount = expiredDocuments.size();
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long driverId;
        private String driverName;
        private DriverStatus driverStatus;
        private List<DriverDocument> expiredDocuments;
        private int expiredCount;

        public Builder driverId(Long driverId) { this.driverId = driverId; return this; }
        public Builder driverName(String driverName) { this.driverName = driverName; return this; }
        public Builder driverStatus(DriverStatus driverStatus) { this.driverStatus = driverStatus; return this; }
        public Builder expiredDocuments(List<DriverDocument> expiredDocuments) {
            this.expiredDocuments = expiredDocuments;
            this.expiredCount = expiredDocuments != null ? expiredDocuments.size() : 0;
            return this;
        }

        public DriverDocumentAlertDTO build() {
            DriverDocumentAlertDTO dto = new DriverDocumentAlertDTO();
            dto.driverId = this.driverId;
            dto.driverName = this.driverName;
            dto.driverStatus = this.driverStatus;
            dto.expiredDocuments = this.expiredDocuments;
            dto.expiredCount = this.expiredCount;
            return dto;
        }
    }

    public Long getDriverId() { return driverId; }
    public String getDriverName() { return driverName; }
    public DriverStatus getDriverStatus() { return driverStatus; }
    public List<DriverDocument> getExpiredDocuments() { return expiredDocuments; }
    public int getExpiredCount() { return expiredCount; }
}