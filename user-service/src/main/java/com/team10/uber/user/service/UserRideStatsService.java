package com.team10.uber.user.service;

import com.team10.uber.user.messaging.events.RideCancelledEvent;
import com.team10.uber.user.messaging.events.RideCompletedEvent;
import com.team10.uber.user.model.User;
import com.team10.uber.user.model.UserRideStatsEvent;
import com.team10.uber.user.repository.UserRepository;
import com.team10.uber.user.repository.UserRideStatsEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserRideStatsService {

    private static final Logger log = LoggerFactory.getLogger(UserRideStatsService.class);

    private final UserRepository userRepository;
    private final UserRideStatsEventRepository statsEventRepository;

    public UserRideStatsService(UserRepository userRepository,
                                UserRideStatsEventRepository statsEventRepository) {
        this.userRepository = userRepository;
        this.statsEventRepository = statsEventRepository;
    }

    @Transactional
    public void applyRideCompleted(RideCompletedEvent event) {
        UserRideStatsEvent statsEvent = statsEventRepository.findByRideId(event.rideId())
                .orElseGet(() -> new UserRideStatsEvent(event.rideId(), event.userId(), fareOrZero(event.fare())));

        if (Boolean.TRUE.equals(statsEvent.getCompletedApplied())) {
            log.info("Skipping duplicate ride.completed for rideId={}", event.rideId());
            return;
        }

        User user = findUser(event.userId());
        user.setTotalRides(safeLong(user.getTotalRides()) + 1);
        user.setTotalSpent(safeDouble(user.getTotalSpent()) + fareOrZero(event.fare()));

        statsEvent.setUserId(event.userId());
        statsEvent.setFare(fareOrZero(event.fare()));
        statsEvent.setCompletedApplied(true);

        userRepository.save(user);
        statsEventRepository.save(statsEvent);
        log.info("Updated user ride stats after ride.completed for userId={} rideId={}", event.userId(), event.rideId());
    }

    @Transactional
    public void applyRideCancelled(RideCancelledEvent event) {
        UserRideStatsEvent statsEvent = statsEventRepository.findByRideId(event.rideId()).orElse(null);
        if (statsEvent == null || !Boolean.TRUE.equals(statsEvent.getCompletedApplied())) {
            log.info("No completed ride stats to reverse for rideId={}", event.rideId());
            return;
        }
        if (Boolean.TRUE.equals(statsEvent.getCancelledApplied())) {
            log.info("Skipping duplicate ride.cancelled for rideId={}", event.rideId());
            return;
        }

        User user = findUser(statsEvent.getUserId());
        user.setTotalRides(Math.max(0L, safeLong(user.getTotalRides()) - 1));
        user.setTotalSpent(Math.max(0.0, safeDouble(user.getTotalSpent()) - fareOrZero(statsEvent.getFare())));

        statsEvent.setCancelledApplied(true);

        userRepository.save(user);
        statsEventRepository.save(statsEvent);
        log.info("Reversed user ride stats after ride.cancelled for userId={} rideId={}",
                statsEvent.getUserId(), event.rideId());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId));
    }

    private Long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private Double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private Double fareOrZero(Double fare) {
        return fare == null ? 0.0 : fare;
    }
}
