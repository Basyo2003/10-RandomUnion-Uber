package com.team10.uber.location.listener;

import com.team10.uber.location.config.LocationRabbitConfig;
import com.team10.uber.location.event.LocationEventActions;
import com.team10.uber.location.model.Location;
import com.team10.uber.location.observer.MongoEventLogger;
import com.team10.uber.location.repository.LocationRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class LocationRideSagaListener {

    private final LocationRepository locationRepository;
    private final MongoEventLogger mongoEventLogger;

    public LocationRideSagaListener(
            LocationRepository locationRepository,
            MongoEventLogger mongoEventLogger
    ) {
        this.locationRepository = locationRepository;
        this.mongoEventLogger = mongoEventLogger;
    }

    @RabbitListener(queues = LocationRabbitConfig.LOCATION_RIDE_QUEUE)
    public void handleRideEvent(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        Map<String, Object> event = mapper.readValue(json, Map.class);

        Long rideId = ((Number) event.get("rideId")).longValue();
        Long userId = ((Number) event.get("userId")).longValue();
        Long driverId = ((Number) event.get("driverId")).longValue();

        if (event.containsKey("fare")) {
            Double fare = ((Number) event.get("fare")).doubleValue();
            handleRideCompleted(rideId, userId, driverId, fare);
        } else if (event.containsKey("reason")) {
            String reason = event.get("reason").toString();
            handleRideCancelled(rideId, userId, driverId, reason);
        }
    }

    private void handleRideCompleted(Long rideId, Long userId, Long driverId, Double fare) {
        Location latestLocation = locationRepository
                .findTopByDriverIdOrderByTimestampDesc(driverId)
                .orElse(null);

        if (latestLocation != null) {
            Map<String, Object> metadata = latestLocation.getMetadata();

            if (metadata == null) {
                metadata = new HashMap<>();
            } else {
                metadata = new HashMap<>(metadata);
            }

            metadata.put("completedRideId", rideId);
            metadata.put("tripCompletedAt", LocalDateTime.now().toString());

            latestLocation.setMetadata(metadata);
            locationRepository.save(latestLocation);
        }

        mongoEventLogger.onEvent(
                LocationEventActions.TRIP_COMPLETED,
                Map.of(
                        "rideId", rideId,
                        "driverId", driverId,
                        "userId", userId,
                        "fare", fare
                )
        );
    }

    private void handleRideCancelled(Long rideId, Long userId, Long driverId, String reason) {
        mongoEventLogger.onEvent(
                LocationEventActions.TRIP_CANCELLED,
                Map.of(
                        "rideId", rideId,
                        "driverId", driverId,
                        "userId", userId,
                        "reason", reason
                )
        );
    }
}