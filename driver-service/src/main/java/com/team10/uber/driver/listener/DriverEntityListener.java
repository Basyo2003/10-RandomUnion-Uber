package com.team10.uber.driver.listener;

import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.service.DriverIndexService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


/**
 * JPA entity listener — auto-syncs Driver changes to Elasticsearch.
 *
 * Why static injection:
 *   JPA instantiates entity listeners via reflection, NOT through Spring.
 *   @Autowired on an instance field would never be called for JPA-created instances.
 *   Workaround: static field + @Autowired setter. Spring calls the setter on the
 *   @Component instance at startup, populating the static field for all JPA instances.
 *
 * §10.2.2f:
 *   @PostPersist → indexDriver(id, "auto_crud_create")
 *   @PostUpdate  → indexDriver(id, "auto_crud_update")
 *   @PostRemove  → removeFromIndex(id)
 *
 * DO NOT fire notifyObservers("DRIVER_DELETED") here.
 * DriverService.deleteDriver() already fires it — firing again = duplicate event.
 */
@Component
public class DriverEntityListener {

    private static final Logger log = LoggerFactory.getLogger(DriverEntityListener.class);

    private static DriverIndexService driverIndexService;

    @Autowired
    public void setDriverIndexService(DriverIndexService service) {
        DriverEntityListener.driverIndexService = service;
    }

    @PostPersist
    public void onPostPersist(Driver driver) {
        if (driverIndexService == null) {
            log.warn("[DriverEntityListener] DriverIndexService not initialized — skipping ES index on PostPersist");
            return;
        }
        Long driverId = driver.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    driverIndexService.indexDriver(driverId, "auto_crud_create");
                } catch (Exception ex) {
                    log.warn("[DriverEntityListener] Failed to auto-index driver {} on create: {}",
                            driverId, ex.getMessage());
                }
            }
        });
    }

    @PostUpdate
    public void onPostUpdate(Driver driver) {
        if (driverIndexService == null) {
            log.warn("[DriverEntityListener] DriverIndexService not initialized — skipping ES index on PostUpdate");
            return;
        }
        Long driverId = driver.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    driverIndexService.indexDriver(driverId, "auto_crud_update");
                } catch (Exception ex) {
                    log.warn("[DriverEntityListener] Failed to auto-index driver {} on update: {}",
                            driverId, ex.getMessage());
                }
            }
        });
    }

    @PostRemove
    public void onPostRemove(Driver driver) {
        if (driverIndexService == null) {
            log.warn("[DriverEntityListener] DriverIndexService not initialized — skipping ES remove on PostRemove");
            return;
        }
        Long driverId = driver.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    driverIndexService.removeFromIndex(driverId);
                } catch (Exception ex) {
                    log.warn("[DriverEntityListener] Failed to remove driver {} from ES on delete: {}",
                            driverId, ex.getMessage());
                }
            }
        });
    }
}
