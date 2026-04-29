package com.team10.uber.user.controller;

import com.team10.uber.user.dto.RoleUpdateRequest;
import com.team10.uber.user.dto.TopRiderDTO;
import com.team10.uber.user.dto.UserProfileDTO;
import com.team10.uber.user.dto.UserRideSummaryDTO;
import com.team10.uber.user.model.User;
import com.team10.uber.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(userService.searchUsers(name, email, role));
    }

    @PutMapping("/{id}/preferences")
    public ResponseEntity<User> updatePreferences(@PathVariable Long id, @RequestBody Map<String, Object> preferences) {
        return ResponseEntity.ok(userService.updatePreferences(id, preferences));
    }

    @GetMapping("/{id}/profile")
    public UserProfileDTO getUserProfile(@PathVariable Long id) {
        return userService.getUserProfile(id);
    }

    @GetMapping("/{id}/ride-summary")
    public ResponseEntity<UserRideSummaryDTO> getUserRideSummary(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserRideSummary(id));
    }

    @GetMapping("/reports/top-riders")
    public ResponseEntity<List<TopRiderDTO>> getTopRiders(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam int limit) {
        return ResponseEntity.ok(userService.getTopRiders(startDate, endDate, limit));
    }

    @GetMapping("/preferences/search")
    public ResponseEntity<List<User>> searchByPreference(
            @RequestParam String key,
            @RequestParam String value) {
        return ResponseEntity.ok(userService.searchByPreference(key, value));
    }

    @GetMapping("/preferences/language")
    public ResponseEntity<List<User>> getUsersByLanguageAndRides(
            @RequestParam String lang,
            @RequestParam(defaultValue = "0") int minRides) {
        return ResponseEntity.ok(userService.searchByLanguageAndRides(lang, minRides));
    }

    @PutMapping("/{id}/deactivate")
    public User deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateUserRole(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUserRole(id, request.getRole()));
    }
}
