package com.team10.uber.driver.repository;

import com.team10.uber.driver.model.DriverDocument;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import org.springframework.data.repository.query.Param;

@Transactional
public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {

    @Query("SELECT dd FROM DriverDocument dd WHERE dd.expiryDate < CURRENT_DATE")
    List<DriverDocument> findExpiredDocuments();

    @Query("SELECT dd FROM DriverDocument dd WHERE dd.driver.id = :driverId")
    List<DriverDocument> findByDriverId(@Param("driverId") Long driverId);

}
