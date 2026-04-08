package com.team10.uber.ride.service;

import com.team10.uber.ride.dto.RideAnalyticsDTO;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.repository.RideRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class RideService {

    private final RideRepository rideRepository;

    public RideService(RideRepository rideRepository) {
        this.rideRepository = rideRepository;
    }

    public Ride createRide(Ride ride) {
        return rideRepository.save(ride);
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public Ride getRideById(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + id));
    }

    public Ride updateRide(Long id, Ride updatedRide) {
        Ride existing = getRideById(id);
        existing.setUserId(updatedRide.getUserId());
        existing.setDriverId(updatedRide.getDriverId());
        existing.setPickupLatitude(updatedRide.getPickupLatitude());
        existing.setPickupLongitude(updatedRide.getPickupLongitude());
        existing.setDropoffLatitude(updatedRide.getDropoffLatitude());
        existing.setDropoffLongitude(updatedRide.getDropoffLongitude());
        existing.setStatus(updatedRide.getStatus());
        existing.setFare(updatedRide.getFare());
        existing.setMetadata(updatedRide.getMetadata());
        return rideRepository.save(existing);
    }

    public void deleteRide(Long id) {
        Ride ride = getRideById(id);
        rideRepository.delete(ride);
    }

    public RideAnalyticsDTO getRideAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Ride> rides = rideRepository.findByRequestedAtBetween(startDateTime, endDateTime);

        long totalRides = rides.size();
        long completedRides = rides.stream()
                .filter(ride -> ride.getStatus() == RideStatus.COMPLETED)
                .count();
        long cancelledRides = rides.stream()
                .filter(ride -> ride.getStatus() == RideStatus.CANCELLED)
                .count();

        double totalRevenue = rides.stream()
                .filter(ride -> ride.getStatus() == RideStatus.COMPLETED)
                .map(Ride::getFare)
                .filter(fare -> fare != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        double averageFare = completedRides > 0 ? totalRevenue / completedRides : 0.0;
        double completionRate = totalRides > 0 ? ((double) completedRides / totalRides) * 100 : 0.0;

        return new RideAnalyticsDTO(
                totalRides,
                completedRides,
                cancelledRides,
                totalRevenue,
                averageFare,
                completionRate
        );
    }
}