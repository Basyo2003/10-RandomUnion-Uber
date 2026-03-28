package com.team10.uber.driver.repository;

import com.team10.uber.driver.model.DriverDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {

    @Query("SELECT dd FROM DriverDocument dd WHERE dd.expiryDate < CURRENT_DATE")
    List<DriverDocument> findExpiredDocuments();
}
