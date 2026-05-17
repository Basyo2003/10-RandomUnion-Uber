package com.team10.uber.user.service;

import com.team10.uber.contracts.feign.PaymentServiceClient;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.contracts.dto.RideSummaryDTO;
import com.team10.uber.user.adapter.ObjectArrayDtoAdapter;
import com.team10.uber.user.adapter.TopRiderDtoAdapter;
import com.team10.uber.user.dto.AddressDTO;
import com.team10.uber.user.dto.AuthEventDTO;
import com.team10.uber.user.dto.TopRiderDTO;
import com.team10.uber.user.dto.UserActivityFeedResponseDTO;
import com.team10.uber.user.dto.UserProfileDTO;
import com.team10.uber.user.dto.UserRideSummaryDTO;
import com.team10.uber.user.enums.Role;
import com.team10.uber.user.enums.Status;
import com.team10.uber.user.event.AuthEvent;
import com.team10.uber.user.messaging.events.UserDeactivatedEvent;
import com.team10.uber.user.messaging.events.UserRegisteredEvent;
import com.team10.uber.user.messaging.publishers.UserEventPublisher;
import com.team10.uber.user.model.SavedAddress;
import com.team10.uber.user.model.User;
import com.team10.uber.user.observer.EntityObserver;
import com.team10.uber.user.observer.AbstractObservableService;
import com.team10.uber.user.observer.MongoEventLogger;
import com.team10.uber.user.repository.SavedAddressRepository;
import com.team10.uber.user.repository.UserRepository;
import com.team10.uber.user.repository.mongo.AuthEventRepository;
import feign.FeignException;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class UserService extends AbstractObservableService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final SavedAddressRepository savedAddressRepository;
    private final AuthEventRepository authEventRepository;
    private final EntityObserver observer;
    private final PasswordEncoder passwordEncoder;
    private final MongoEventLogger mongoEventLogger;
    private final UserEventPublisher userEventPublisher;
    private final RideServiceClient rideServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter = new ObjectArrayDtoAdapter();
    private final TopRiderDtoAdapter topRiderDtoAdapter = new TopRiderDtoAdapter();

    public UserService(UserRepository userRepository,
                       SavedAddressRepository savedAddressRepository,
                       AuthEventRepository authEventRepository,
                       EntityObserver observer,
                       PasswordEncoder passwordEncoder,
                       MongoEventLogger mongoEventLogger,
                       UserEventPublisher userEventPublisher,
                       RideServiceClient rideServiceClient,
                       PaymentServiceClient paymentServiceClient) {
        this.userRepository = userRepository;
        this.savedAddressRepository = savedAddressRepository;
        this.authEventRepository = authEventRepository;
        this.observer = observer;
        this.passwordEncoder = passwordEncoder;
        this.mongoEventLogger = mongoEventLogger;
        this.userEventPublisher = userEventPublisher;
        this.rideServiceClient = rideServiceClient;
        this.paymentServiceClient = paymentServiceClient;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Caching(evict = {
            @CacheEvict(value = "user-service::S1-F1", allEntries = true),
            @CacheEvict(value = "user-service::S1-F6", allEntries = true)
    })
    public User createUser(User user) {
        User saved = userRepository.save(user);
        notifyObservers("USER_CREATED", saved);
        return saved;
    }

    public void logAuthEvent(Long userId, String action, Map<String, String> details) {
        Map<String, Object> payload = new java.util.HashMap<>(details);
        payload.put("userId", userId);
        notifyObservers(action, payload);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Cacheable(value = "user-service::user", key = "#id")
    public User getUserById(Long id) {
        return findUserByIdOrThrow(id);
    }

    @Cacheable(value = "user-service::S1-F12", key = "#id + ':' + #page + ':' + #size")
    public UserActivityFeedResponseDTO getUserActivityFeed(Long id, int page, int size) {
        findUserByIdOrThrow(id);

        Page<AuthEvent> eventPage = authEventRepository.findByUserIdOrderByTimestampDesc(
                id, PageRequest.of(page, size));

        List<AuthEventDTO> content = eventPage.getContent().stream()
                .map(this::toAuthEventDto)
                .toList();

        UserActivityFeedResponseDTO response = new UserActivityFeedResponseDTO();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(eventPage.getTotalElements());
        return response;
    }

    @Caching(evict = {
            @CacheEvict(value = "user-service::user", key = "#id"),
            @CacheEvict(value = "user-service::S1-F8", key = "#id"),
            @CacheEvict(value = "user-service::S1-F3", key = "#id"),
            @CacheEvict(value = "user-service::S1-F1", allEntries = true),
            @CacheEvict(value = "user-service::S1-F6", allEntries = true)
    })
    public User updateUser(Long id, User updatedUser) {
        User existingUser = findUserByIdOrThrow(id);
        existingUser.setName(updatedUser.getName());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setPassword(updatedUser.getPassword());
        existingUser.setPhone(updatedUser.getPhone());
        existingUser.setRole(updatedUser.getRole());
        existingUser.setStatus(updatedUser.getStatus());
        existingUser.setPreferences(updatedUser.getPreferences());
        User saved = userRepository.save(existingUser);
        notifyObservers("USER_UPDATED", saved);
        return saved;
    }

    @Caching(evict = {
            @CacheEvict(value = "user-service::user", key = "#id"),
            @CacheEvict(value = "user-service::S1-F8", key = "#id"),
            @CacheEvict(value = "user-service::S1-F3", key = "#id"),
            @CacheEvict(value = "user-service::S1-F1", allEntries = true),
            @CacheEvict(value = "user-service::S1-F6", allEntries = true)
    })
    public void deleteUser(Long id) {
        User user = findUserByIdOrThrow(id);
        userRepository.delete(user);
        notifyObservers("USER_DELETED", id);
    }

    @Cacheable(value = "user-service::S1-F1", key = "#name + '_' + #email + '_' + #role")
    public List<User> searchUsers(String name, String email, String role) {
        String searchName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
        String searchEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
        String searchRole = (role != null && !role.trim().isEmpty()) ? role.trim() : null;
        return userRepository.searchUsers(searchName, searchEmail, searchRole);
    }

    @Caching(evict = {
            @CacheEvict(value = "user-service::user", key = "#id"),
            @CacheEvict(value = "user-service::S1-F8", key = "#id"),
            @CacheEvict(value = "user-service::S1-F3", key = "#id"),
            @CacheEvict(value = "user-service::S1-F1", allEntries = true)
    })
    public User updatePreferences(Long id, Map<String, Object> newPreferences) {
        User user = findUserByIdOrThrow(id);
        Map<String, Object> merged = new HashMap<>(user.getPreferences());
        merged.putAll(newPreferences);
        user.setPreferences(merged);
        User saved = userRepository.save(user);
        notifyObservers("USER_UPDATED", Map.of("userId", id, "updatedFields", "preferences"));
        return saved;
    }

    @Cacheable(value = "user-service::S1-F8", key = "#id")
    public UserProfileDTO getUserProfile(Long id) {
        User user = findUserByIdOrThrow(id);
        UserProfileDTO userProfileDTO = new UserProfileDTO();
        userProfileDTO.setUserId(user.getId());
        userProfileDTO.setName(user.getName());
        userProfileDTO.setEmail(user.getEmail());
        userProfileDTO.setPhone(user.getPhone());
        userProfileDTO.setPreferences(user.getPreferences());

        List<AddressDTO> addressDTOList = new ArrayList<>();
        for (SavedAddress savedAddress : user.getSavedAddresses()) {
            AddressDTO addressDTO = new AddressDTO();
            addressDTO.setId(savedAddress.getId());
            addressDTO.setLabel(savedAddress.getLabel());
            addressDTO.setAddress(savedAddress.getAddress());
            addressDTO.setLatitude(savedAddress.getLatitude());
            addressDTO.setLongitude(savedAddress.getLongitude());
            addressDTO.setIsDefault(savedAddress.getIsDefault());
            addressDTO.setMetadata(savedAddress.getMetadata());
            addressDTO.setCreatedAt(savedAddress.getCreatedAt());
            addressDTOList.add(addressDTO);
        }
        userProfileDTO.setSavedAddresses(addressDTOList);
        userProfileDTO.setTotalAddresses(addressDTOList.size());
        return userProfileDTO;
    }

    // S1-F3: Refactored to use Feign → ride-service
    @Cacheable(value = "user-service::S1-F3", key = "#id")
    public UserRideSummaryDTO getUserRideSummary(Long id) {
        User user = findUserByIdOrThrow(id);
        MDC.put("userId", id.toString());
        try {
            log.info("Calling RideServiceClient.getUserRideSummary with args={}", id);
            RideSummaryDTO summary = rideServiceClient.getUserRideSummary(id);
            log.info("RideServiceClient.getUserRideSummary returned successfully");
            return new UserRideSummaryDTO(
                    user.getId(),
                    user.getName(),
                    summary.totalRides(),
                    summary.completedRides(),
                    summary.cancelledRides(),
                    summary.totalSpent(),
                    summary.averageFare()
            );
        } catch (FeignException.NotFound e) {
            log.warn("Feign call to ride-service failed: {}", e.getMessage());
            return new UserRideSummaryDTO(user.getId(), user.getName(), 0L, 0L, 0L, 0.0, 0.0);
        } catch (FeignException e) {
            log.warn("Feign call to ride-service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ride service temporarily unavailable");
        } finally {
            MDC.remove("userId");
        }
    }

    // S1-F6: Refactored to use Feign → payment-service per user
    @Cacheable(value = "user-service::S1-F6", key = "#startDate + '_' + #endDate + '_' + #limit")
    public List<TopRiderDTO> getTopRiders(String startDate, String endDate, int limit) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            if (start.isAfter(end)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "startDate must be before endDate");
            }
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date format. Use yyyy-MM-dd");
        }

        // Cap at 100 users per §2.12 N+1 hazard guidance
        List<User> users = userRepository.findAll(PageRequest.of(0, 100)).getContent();

        List<TopRiderDTO> topRiders = new ArrayList<>();
        for (User user : users) {
            log.info("Calling PaymentServiceClient.getUserPaymentTotal with args={}", user.getId());
            try {
                BigDecimal total = paymentServiceClient.getUserPaymentTotal(
                        user.getId(), startDate, endDate);
                log.info("PaymentServiceClient.getUserPaymentTotal returned successfully");
                if (total != null && total.compareTo(BigDecimal.ZERO) > 0) {
                    topRiders.add(new TopRiderDTO(
                            user.getId(),
                            user.getName(),
                            total.doubleValue(),
                            0L
                    ));
                }
            } catch (FeignException e) {
                log.warn("Feign call to payment-service failed for userId={}: {}",
                        user.getId(), e.getMessage());
            }
        }

        topRiders.sort((a, b) -> Double.compare(b.getTotalSpent(), a.getTotalSpent()));
        return topRiders.stream().limit(limit).toList();
    }

    @Cacheable(value = "user-service::S1-F5", key = "#key + '_' + #value")
    public List<User> searchByPreference(String key, String value) {
        if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Key and value must not be blank");
        }
        return userRepository.searchByPreference(key, value);
    }

    // S1-F9: Refactored to use Feign → ride-service per matching user
    @Cacheable(value = "user-service::S1-F9", key = "#lang + '_' + #minRides")
    public List<User> searchByLanguageAndRides(String lang, Integer minRides) {
        if (lang == null || lang.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language is required");
        }
        int rides = (minRides != null) ? minRides : 0;

        // Query local user-postgres for language preference, cap at 100 per §2.12
        List<User> candidates = userRepository.findByLanguagePreference(lang, 100);

        List<User> result = new ArrayList<>();
        for (User user : candidates) {
            log.info("Calling RideServiceClient.getCompletedRideCount with args={}", user.getId());
            try {
                long completedCount = rideServiceClient.getCompletedRideCount(user.getId());
                log.info("RideServiceClient.getCompletedRideCount returned successfully");
                if (completedCount >= rides) {
                    result.add(user);
                }
            } catch (FeignException e) {
                log.warn("Feign call to ride-service failed for userId={}: {}",
                        user.getId(), e.getMessage());
            }
        }
        return result;
    }

    // S1-F4: Refactored to use Feign → ride-service for active-count
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "user-service::user", key = "#id"),
            @CacheEvict(value = "user-service::S1-F8", key = "#id"),
            @CacheEvict(value = "user-service::S1-F1", allEntries = true)
    })
    public User deactivateUser(Long id) {
        User user = findUserByIdOrThrow(id);
        if (user.getStatus() == Status.DEACTIVATED) {
            return user;
        }

        MDC.put("userId", id.toString());
        try {
            log.info("Calling RideServiceClient.getActiveRideCount with args={}", id);
            int activeCount = rideServiceClient.getActiveRideCount(id);
            log.info("RideServiceClient.getActiveRideCount returned successfully");
            if (activeCount > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has active rides");
            }
        } catch (FeignException e) {
            log.warn("Feign call to ride-service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ride service temporarily unavailable");
        } finally {
            MDC.remove("userId");
        }

        user.setStatus(Status.DEACTIVATED);
        User saved = userRepository.save(user);
        log.info("User {} saved with status=DEACTIVATED", saved.getId());
        notifyObservers("USER_DEACTIVATED", Map.of("userId", id, "status", "DEACTIVATED"));
        afterCommit(() -> userEventPublisher.publishUserDeactivated(
                new UserDeactivatedEvent(saved.getId())));
        return saved;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "user-service::S1-F8", key = "#userId"),
            @CacheEvict(value = "user-service::S1-F1", allEntries = true)
    })
    public UserProfileDTO setDefaultSavedAddress(Long userId, Long addressId) {
        User user = findUserByIdOrThrow(userId);
        SavedAddress targetAddress = savedAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Saved address not found"));
        if (!targetAddress.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Saved address does not belong to this user");
        }
        for (SavedAddress address : user.getSavedAddresses()) {
            address.setIsDefault(false);
        }
        targetAddress.setIsDefault(true);
        userRepository.save(user);
        notifyObservers("DEFAULT_ADDRESS_SET", Map.of("userId", userId, "addressId", addressId));
        return getUserProfile(userId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "user-service::user", key = "#id"),
            @CacheEvict(value = "user-service::S1-F12", allEntries = true)
    })
    public User updateUserRole(Long id, String requestedRole) {
        User user = findUserByIdOrThrow(id);

        if (requestedRole == null || requestedRole.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }

        Role newRole;
        try {
            newRole = Role.valueOf(requestedRole.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role value");
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);
        User saved = userRepository.save(user);
        notifyObservers("ROLE_CHANGED", Map.of(
                "userId", id,
                "oldRole", oldRole.name(),
                "newRole", newRole.name()));
        return saved;
    }

    @Transactional
    public User registerUser(User user) {
        if (user.getName() == null || user.getName().isBlank() ||
                user.getEmail() == null || user.getEmail().isBlank() ||
                user.getPassword() == null || user.getPassword().isBlank() ||
                user.getPhone() == null || user.getPhone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field cannot be blank");
        }
        if (userRepository.existsByEmail(user.getEmail()) ||
                userRepository.existsByPhone(user.getPhone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email or Phone already exists");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(Role.RIDER);
        user.setStatus(Status.ACTIVE);

        User savedUser = userRepository.save(user);
        observer.onEvent("REGISTERED", savedUser);
        afterCommit(() -> userEventPublisher.publishUserRegistered(
                new UserRegisteredEvent(savedUser.getId(), savedUser.getEmail(),
                        savedUser.getRole().name())));
        return savedUser;
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void logEvent(Long userId, String action, Map<String, Object> details) {
        try {
            Map<String, Object> payload = new HashMap<>(details);
            payload.put("userId", userId);
            notifyObservers(action, payload);
        } catch (Exception e) {
            // soft dependency, never rethrow
        }
    }

    private User findUserByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + id));
    }

    private AuthEventDTO toAuthEventDto(AuthEvent event) {
        AuthEventDTO dto = new AuthEventDTO();
        dto.setId(event.getId());
        dto.setUserId(event.getUserId());
        dto.setAction(event.getAction());
        dto.setTimestamp(event.getTimestamp());
        dto.setDetails(event.getDetails());
        return dto;
    }
}