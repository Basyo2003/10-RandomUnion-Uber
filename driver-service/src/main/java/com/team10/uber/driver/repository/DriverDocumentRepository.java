package com.team10.uber.driver.repository;

import com.team10.uber.driver.model.DriverDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {
}
