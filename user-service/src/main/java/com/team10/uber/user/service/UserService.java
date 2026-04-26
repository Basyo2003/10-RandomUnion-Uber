package com.team10.uber.user.service;

import com.team10.uber.user.dto.AddressDTO;
import com.team10.uber.user.dto.TopRiderDTO;
import com.team10.uber.user.dto.UserProfileDTO;
import com.team10.uber.user.dto.UserRideSummaryDTO;
import com.team10.uber.user.enums.Status;
import com.team10.uber.user.model.SavedAddress;
import com.team10.uber.user.model.User;
import com.team10.uber.user.repository.SavedAddressRepository;
import com.team10.uber.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SavedAddressRepository savedAddressRepository;

    public UserService(UserRepository userRepository,
                       SavedAddressRepository savedAddressRepository) {
        this.userRepository = userRepository;
        this.savedAddressRepository = savedAddressRepository;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User createUser(User user) {
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return findUserByIdOrThrow(id);
    }

    public User updateUser(Long id, User updatedUser) {
        User existingUser = findUserByIdOrThrow(id);
        existingUser.setName(updatedUser.getName());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setPassword(updatedUser.getPassword());
        existingUser.setPhone(updatedUser.getPhone());
        existingUser.setRole(updatedUser.getRole());
        existingUser.setStatus(updatedUser.getStatus());
        existingUser.setPreferences(updatedUser.getPreferences());
        return userRepository.save(existingUser);
    }

    public void deleteUser(Long id) {
        User user = findUserByIdOrThrow(id);
        userRepository.delete(user);
    }

    @Cacheable(value = "user-service::S1-F1", key = "#name + '_' + #email + '_' + #role")
    public List<User> searchUsers(String name, String email, String role) {
        String searchName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
        String searchEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
        String searchRole = (role != null && !role.trim().isEmpty()) ? role.trim() : null;
        return userRepository.searchUsers(searchName, searchEmail, searchRole);
    }

    public User updatePreferences(Long id, Map<String, Object> newPreferences) {
        User user = findUserByIdOrThrow(id);
        Map<String, Object> merged = new HashMap<>(user.getPreferences());
        merged.putAll(newPreferences);
        user.setPreferences(merged);
        return userRepository.save(user);
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

    @Cacheable(value = "user-service::S1-F3", key = "#id")
    public UserRideSummaryDTO getUserRideSummary(Long id) {
        User user = findUserByIdOrThrow(id);
        List<Object[]> resultRows = userRepository.getRideSummaryByUserId(id);
        Object[] result = resultRows.isEmpty() ? new Object[]{0L, 0L, 0L, 0.0, 0.0} : resultRows.get(0);

        Long totalRides = result[0] == null ? 0L : ((Number) result[0]).longValue();
        Long completedRides = result[1] == null ? 0L : ((Number) result[1]).longValue();
        Long cancelledRides = result[2] == null ? 0L : ((Number) result[2]).longValue();
        Double totalSpent = result[3] == null ? 0.0 : ((Number) result[3]).doubleValue();
        Double averageFare = result[4] == null ? 0.0 : ((Number) result[4]).doubleValue();

        return new UserRideSummaryDTO(
                user.getId(), user.getName(),
                totalRides, completedRides, cancelledRides,
                totalSpent, averageFare);
    }

    @Cacheable(value = "user-service::S1-F6", key = "#startDate + '_' + #endDate + '_' + #limit")
    public List<TopRiderDTO> getTopRiders(String startDate, String endDate, int limit) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            if (start.isAfter(end)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
            }
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use yyyy-MM-dd");
        }

        String startDateTime = startDate + " 00:00:00";
        String endDateTime = endDate + " 23:59:59";

        List<Object[]> results = userRepository.getTopRidersBySpending(startDateTime, endDateTime, limit);
        List<TopRiderDTO> topRiders = new ArrayList<>();
        for (Object[] row : results) {
            Long userId = ((Number) row[0]).longValue();
            String name = (String) row[1];
            Double totalSpent = ((Number) row[2]).doubleValue();
            Long rideCount = ((Number) row[3]).longValue();
            topRiders.add(new TopRiderDTO(userId, name, totalSpent, rideCount));
        }
        return topRiders;
    }

    @Cacheable(value = "user-service::S1-F5", key = "#key + '_' + #value")
    public List<User> searchByPreference(String key, String value) {
        if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key and value must not be blank");
        }
        return userRepository.searchByPreference(key, value);
    }

    @Cacheable(value = "user-service::S1-F9", key = "#lang + '_' + #minRides")
    public List<User> searchByLanguageAndRides(String lang, Integer minRides) {
        if (lang == null || lang.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language is required");
        }
        int rides = (minRides != null) ? minRides : 0;
        return userRepository.findUsersByLanguageAndCompletedRides(lang, rides);
    }

    @Transactional
    public User deactivateUser(Long id) {
        User user = findUserByIdOrThrow(id);
        if (userRepository.hasActiveRides(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has active rides");
        }
        user.setStatus(Status.DEACTIVATED);
        return userRepository.save(user);
    }

    @Transactional
    public UserProfileDTO setDefaultSavedAddress(Long userId, Long addressId) {
        User user = findUserByIdOrThrow(userId);
        SavedAddress targetAddress = savedAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved address not found"));
        if (!targetAddress.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saved address does not belong to this user");
        }
        for (SavedAddress address : user.getSavedAddresses()) {
            address.setIsDefault(false);
        }
        targetAddress.setIsDefault(true);
        userRepository.save(user);
        return getUserProfile(userId);
    }

    private User findUserByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + id));
    }
}