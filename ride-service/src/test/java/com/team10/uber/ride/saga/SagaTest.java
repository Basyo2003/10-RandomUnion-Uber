package com.team10.uber.ride.saga;

import com.team10.uber.contracts.dto.DriverDTO;
import com.team10.uber.contracts.dto.LocationDTO;
import com.team10.uber.contracts.dto.UserDTO;
import com.team10.uber.contracts.events.PaymentCompletedEvent;
import com.team10.uber.contracts.events.PaymentFailedEvent;
import com.team10.uber.contracts.events.RideCancelledEvent;
import com.team10.uber.contracts.events.RideCompletedEvent;
import com.team10.uber.contracts.feign.DriverServiceClient;
import com.team10.uber.contracts.feign.LocationServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.ride.enums.RideStatus;
import com.team10.uber.ride.exception.InvalidRideStatusException;
import com.team10.uber.ride.messaging.consumers.PaymentEventConsumer;
import com.team10.uber.ride.messaging.publishers.RideEventPublisher;
import com.team10.uber.ride.model.Ride;
import com.team10.uber.ride.observer.MongoEventLogger;
import com.team10.uber.ride.repository.RideRepository;
import com.team10.uber.ride.repository.RideStopRepository;
import com.team10.uber.ride.service.CacheInvalidationService;
import com.team10.uber.ride.service.RideService;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaTest {

    @Mock RideRepository rideRepository;
    @Mock RideStopRepository rideStopRepository;
    @Mock MongoEventLogger mongoEventLogger;
    @Mock CacheInvalidationService cacheInvalidation;
    @Mock Driver neo4jDriver;
    @Mock DriverServiceClient driverServiceClient;
    @Mock UserServiceClient userServiceClient;
    @Mock LocationServiceClient locationServiceClient;
    @Mock RideEventPublisher rideEventPublisher;

    RideService rideService;
    PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        rideService = new RideService(
                rideRepository, rideStopRepository, mongoEventLogger,
                cacheInvalidation, neo4jDriver,
                driverServiceClient, userServiceClient, locationServiceClient,
                rideEventPublisher
        );
        paymentEventConsumer = new PaymentEventConsumer(rideRepository, rideEventPublisher);
    }

    // ── Scenario A — Happy path ────────────────────────────────────────────────

    @Test
    void scenarioA_completeRide_allPreCheckPass_publishesRideCompleted() {
        // setup: ride IN_PROGRESS, user ACTIVE, driver BUSY, location 1 min ago
        Ride ride = rideWithStatus(10L, 1L, 5L, RideStatus.IN_PROGRESS);
        ride.setPickupLatitude(30.0); ride.setPickupLongitude(31.0);
        ride.setDropoffLatitude(30.1); ride.setDropoffLongitude(31.1);

        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(userServiceClient.getUser(1L)).thenReturn(
                new UserDTO(1L, "Ahmed", "a@b.com", "01000000", "USER", "ACTIVE"));
        when(driverServiceClient.getDriver(5L)).thenReturn(
                new DriverDTO(5L, "Omar", "01111111", "BUSY", 4.8, 100, Map.of("vehicleType", "SEDAN")));
        when(locationServiceClient.getRecentLocationForDriver(5L)).thenReturn(
                new LocationDTO(1L, 5L, 30.05, 31.05, LocalDateTime.now().minusMinutes(1)));

        // action
        Ride result = rideService.completeRide(10L);

        // expect: status COMPLETED, fare computed, ride.completed published
        assertThat(result.getStatus()).isEqualTo(RideStatus.COMPLETED);
        assertThat(result.getFare()).isPositive();
        assertThat(result.getCompletedAt()).isNotNull();

        ArgumentCaptor<RideCompletedEvent> captor = ArgumentCaptor.forClass(RideCompletedEvent.class);
        verify(rideEventPublisher).publishRideCompleted(captor.capture());
        RideCompletedEvent event = captor.getValue();
        assertThat(event.rideId()).isEqualTo(10L);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.driverId()).isEqualTo(5L);
        assertThat(event.fare()).isPositive();
    }

    @Test
    void scenarioA_paymentCompleted_setsRidePaid() {
        // setup: ride arrives at PAYMENT_PENDING after payment-service consumed ride.completed
        Ride ride = rideWithStatus(10L, 1L, 5L, RideStatus.PAYMENT_PENDING);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // action: payment.completed arrives at ride.saga-feedback
        paymentEventConsumer.onPaymentCompleted(new PaymentCompletedEvent(99L, 10L, 150.0), emptyMessage());

        // expect: ride status = PAID
        ArgumentCaptor<Ride> saved = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RideStatus.PAID);
    }

    // ── Scenario B — Payment failure + compensation ────────────────────────────

    @Test
    void scenarioB_paymentFailed_setsPaymentFailedAndPublishesRideCancelled() {
        // setup: ride at PAYMENT_PENDING
        Ride ride = rideWithStatus(10L, 1L, 5L, RideStatus.PAYMENT_PENDING);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // action: payment.failed arrives (e.g. unsupported payment method)
        paymentEventConsumer.onPaymentFailed(new PaymentFailedEvent(99L, 10L, "unsupported payment method"), emptyMessage());

        // expect: ride = PAYMENT_FAILED, ride.cancelled published with compensation reason
        ArgumentCaptor<Ride> savedRide = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(savedRide.capture());
        assertThat(savedRide.getValue().getStatus()).isEqualTo(RideStatus.PAYMENT_FAILED);

        ArgumentCaptor<RideCancelledEvent> cancelEvent = ArgumentCaptor.forClass(RideCancelledEvent.class);
        verify(rideEventPublisher).publishRideCancelled(cancelEvent.capture());
        assertThat(cancelEvent.getValue().rideId()).isEqualTo(10L);
        assertThat(cancelEvent.getValue().reason()).contains("Payment failed");
    }

    @Test
    void scenarioB_paymentFailed_idempotent_alreadyCancelled_noOp() {
        // if ride is already CANCELLED, a duplicate payment.failed must not mutate again
        Ride ride = rideWithStatus(10L, 1L, 5L, RideStatus.CANCELLED);
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));

        paymentEventConsumer.onPaymentFailed(new PaymentFailedEvent(99L, 10L, "dup"), emptyMessage());

        verify(rideRepository, never()).save(any());
        verify(rideEventPublisher, never()).publishRideCancelled(any());
    }

    // ── Scenario C — Pre-check failure (stale / missing location) ─────────────

    @Test
    void scenarioC_staleLocation_returns400_noEventPublished() {
        // setup: location returns 404 (timestamp older than 5 min at location-service)
        Ride ride = rideWithStatus(10L, 1L, 5L, RideStatus.IN_PROGRESS);
        ride.setPickupLatitude(30.0); ride.setPickupLongitude(31.0);
        ride.setDropoffLatitude(30.1); ride.setDropoffLongitude(31.1);

        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(userServiceClient.getUser(1L)).thenReturn(
                new UserDTO(1L, "Ahmed", "a@b.com", "01000000", "USER", "ACTIVE"));
        when(driverServiceClient.getDriver(5L)).thenReturn(
                new DriverDTO(5L, "Omar", "01111111", "BUSY", 4.8, 100, Map.of()));
        when(locationServiceClient.getRecentLocationForDriver(5L))
                .thenThrow(feignNotFound());

        // action + expect: 400, no event
        assertThatThrownBy(() -> rideService.completeRide(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("driver not actively tracked");

        verify(rideEventPublisher, never()).publishRideCompleted(any());
        verify(rideRepository, never()).save(any());
    }

    @Test
    void scenarioC_deactivatedUser_returns400_noEventPublished() {
        Ride ride = rideWithStatus(10L, 1L, 5L, RideStatus.IN_PROGRESS);
        ride.setPickupLatitude(30.0); ride.setPickupLongitude(31.0);
        ride.setDropoffLatitude(30.1); ride.setDropoffLongitude(31.1);

        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(userServiceClient.getUser(1L)).thenReturn(
                new UserDTO(1L, "Ahmed", "a@b.com", "01000000", "USER", "DEACTIVATED"));

        assertThatThrownBy(() -> rideService.completeRide(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User is not active");

        verify(rideEventPublisher, never()).publishRideCompleted(any());
    }

    @Test
    void scenarioC_driverNotBusy_returns400_noEventPublished() {
        Ride ride = rideWithStatus(10L, 1L, 5L, RideStatus.IN_PROGRESS);
        ride.setPickupLatitude(30.0); ride.setPickupLongitude(31.0);
        ride.setDropoffLatitude(30.1); ride.setDropoffLongitude(31.1);

        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(userServiceClient.getUser(1L)).thenReturn(
                new UserDTO(1L, "Ahmed", "a@b.com", "01000000", "USER", "ACTIVE"));
        when(driverServiceClient.getDriver(5L)).thenReturn(
                new DriverDTO(5L, "Omar", "01111111", "OFFLINE", 4.8, 100, Map.of()));

        assertThatThrownBy(() -> rideService.completeRide(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Driver is not BUSY");

        verify(rideEventPublisher, never()).publishRideCompleted(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Ride rideWithStatus(Long rideId, Long userId, Long driverId, RideStatus status) {
        Ride ride = new Ride();
        ride.setId(rideId);
        ride.setUserId(userId);
        ride.setDriverId(driverId);
        ride.setStatus(status);
        return ride;
    }

    private FeignException.NotFound feignNotFound() {
        return new FeignException.NotFound(
                "Not Found",
                Request.create(Request.HttpMethod.GET, "/test", Map.of(), null, null, null),
                null, Map.of()
        );
    }

    private Message emptyMessage() {
        return new Message(new byte[0], new MessageProperties());
    }
}
