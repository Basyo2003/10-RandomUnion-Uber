package com.team10.uber.user.repository;

import com.team10.uber.user.model.SavedAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedAddressRepository extends JpaRepository<SavedAddress, Long> {
}
