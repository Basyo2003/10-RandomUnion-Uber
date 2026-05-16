package com.team10.uber.user.repository;

import com.team10.uber.user.model.UserRideStatsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRideStatsEventRepository extends JpaRepository<UserRideStatsEvent, Long> {

    Optional<UserRideStatsEvent> findByRideId(Long rideId);
}
