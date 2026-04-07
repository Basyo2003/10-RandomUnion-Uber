package com.team10.uber.user.service;

import com.team10.uber.user.enums.Role;
import com.team10.uber.user.dto.AddressDTO;
import com.team10.uber.user.dto.UserProfileDTO;
import com.team10.uber.user.model.SavedAddress;
import com.team10.uber.user.model.User;
import com.team10.uber.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.user.dto.UserRideSummaryDTO;
import com.team10.uber.user.dto.TopRiderDTO;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(User user) {
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public User updateUser(Long id, User updatedUser) {
        User existingUser = getUserById(id);

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
        User user = getUserById(id);
        userRepository.delete(user);
    }

    public List<User> searchUsers(String name, String email, String role) {
        // Optional: Trim strings or handle empty strings as nulls
        String searchName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
        String searchEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
        String searchRole = (role != null && !role.trim().isEmpty()) ? role.trim() : null;

        return userRepository.searchUsers(searchName, searchEmail, searchRole);
    }
    public User updatePreferences(Long id, Map<String, Object> newPreferences) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        Map<String, Object> merged = new HashMap<>(user.getPreferences());
        merged.putAll(newPreferences);
        user.setPreferences(merged);

        return userRepository.save(user);
    }
    public UserProfileDTO getUserProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserProfileDTO userProfileDTO = new UserProfileDTO();
        userProfileDTO.setUserId(user.getId());
        userProfileDTO.setName(user.getName());
        userProfileDTO.setEmail(user.getEmail());
        userProfileDTO.setPhone(user.getPhone());
        userProfileDTO.setPreferences(user.getPreferences());

        List<AddressDTO> addressDTOList = new ArrayList<>();

        for (SavedAddress savedAddress : user.getSavedAddresses()) {
            AddressDTO addressDTO = new AddressDTO();
            addressDTO.setLabel(savedAddress.getLabel());
            addressDTO.setAddress(savedAddress.getAddress());
            addressDTO.setLatitude(savedAddress.getLatitude());
            addressDTO.setLongitude(savedAddress.getLongitude());
            addressDTO.setIsDefault(savedAddress.getIsDefault());
            addressDTO.setMetadata(savedAddress.getMetadata());

            addressDTOList.add(addressDTO);
        }

        userProfileDTO.setSavedAddresses(addressDTOList);
        userProfileDTO.setTotalAddresses(addressDTOList.size());

        return userProfileDTO;
    }

    public UserRideSummaryDTO getUserRideSummary(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Object[]> resultRows = userRepository.getRideSummaryByUserId(id);

        // Safeguard to handle when there are no rows returned
        Object[] result = resultRows.isEmpty() ? new Object[]{0L, 0L, 0L, 0.0, 0.0} : resultRows.get(0);

        Long totalRides = 0L;
        Long completedRides = 0L;
        Long cancelledRides = 0L;
        Double totalSpent = 0.0;
        Double averageFare = 0.0;

        // Safely cast the results from Object[] and handle any null or unexpected values
        if (result[0] != null) totalRides = ((Number) result[0]).longValue();
        if (result[1] != null) completedRides = ((Number) result[1]).longValue();
        if (result[2] != null) cancelledRides = ((Number) result[2]).longValue();
        if (result[3] != null) totalSpent = ((Number) result[3]).doubleValue();
        if (result[4] != null) averageFare = ((Number) result[4]).doubleValue();

        return new UserRideSummaryDTO(
                user.getId(),
                user.getName(),
                totalRides,
                completedRides,
                cancelledRides,
                totalSpent,
                averageFare
        );
    }

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

        // Fix: include the full end day by appending time boundaries
        String startDateTime = startDate + " 00:00:00";
        String endDateTime   = endDate   + " 23:59:59";

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

    public List<User> searchByPreference(String key, String value) {
        // Requirement: throw (400) if key or value is blank
        if (key == null || key.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key and value must not be blank");
        }

        return userRepository.searchByPreference(key, value);
    }

    public List<User> searchByLanguageAndRides(String lang, Integer minRides) {
        if (lang == null || lang.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language is required");
        }
        int rides = (minRides != null) ? minRides : 0;

        return userRepository.findUsersByLanguageAndCompletedRides(lang, rides);
    }
}