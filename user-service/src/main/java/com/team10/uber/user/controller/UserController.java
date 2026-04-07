package com.team10.uber.user.controller;

import com.team10.uber.user.enums.Role;
import com.team10.uber.user.model.User;
import com.team10.uber.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.team10.uber.user.dto.UserProfileDTO;
import com.team10.uber.user.dto.UserRideSummaryDTO;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.user.dto.TopRiderDTO;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        return userService.updateUser(id, updatedUser);
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return "User deleted successfully";
    }

    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "role", required = false) String role) {

        List<User> users = userService.searchUsers(name, email, role);
        return ResponseEntity.ok(users);
    }

    // S1-F2: Update User Preferences (JSONB)
    @PutMapping("/{id}/preferences")
    public ResponseEntity<User> updatePreferences(@PathVariable Long id, @RequestBody Map<String, Object> preferences) {
        try {
            User updated = userService.updatePreferences(id, preferences);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/profile")
    public UserProfileDTO getUserProfile(@PathVariable Long id) {
        return userService.getUserProfile(id);
    }

    @GetMapping("/{id}/ride-summary")
    public ResponseEntity<UserRideSummaryDTO> getUserRideSummary(@PathVariable Long id) {
        try {
            UserRideSummaryDTO summary = userService.getUserRideSummary(id);
            return ResponseEntity.ok(summary);
        } catch (ResponseStatusException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // S1-F6: Top Riders by Spending
    @GetMapping("/reports/top-riders")
    public ResponseEntity<?> getTopRiders(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam int limit) {
        try {
            List<TopRiderDTO> result = userService.getTopRiders(startDate, endDate, limit);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @GetMapping("/preferences/search")
    public ResponseEntity<List<User>> searchByPreference(
            @RequestParam String key,
            @RequestParam String value) {

        List<User> users = userService.searchByPreference(key, value);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/preferences/language")
    public ResponseEntity<List<User>> getUsersByLanguageAndRides(
            @RequestParam String lang,
            @RequestParam(defaultValue = "0") int minRides) {

        // The Service layer handles the (400) validation and the Native SQL call
        List<User> users = userService.searchByLanguageAndRides(lang, minRides);
        return ResponseEntity.ok(users);
    }
  
    @PutMapping("/{id}/deactivate")
    public User deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }

    @PutMapping("/{userId}/addresses/{addressId}/default")
    public UserProfileDTO setDefaultSavedAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        return userService.setDefaultSavedAddress(userId, addressId);
    }
}
