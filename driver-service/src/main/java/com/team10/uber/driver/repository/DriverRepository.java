package com.team10.uber.driver.repository;

import com.team10.uber.driver.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {
}
