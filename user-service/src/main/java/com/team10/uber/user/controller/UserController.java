package com.team10.uber.user.controller;

import com.team10.uber.user.dto.RoleUpdateRequest;
import com.team10.uber.user.dto.TopRiderDTO;
import com.team10.uber.user.dto.UserActivityFeedResponseDTO;
import com.team10.uber.user.dto.UserProfileDTO;
import com.team10.uber.user.dto.UserRideSummaryDTO;
import com.team10.uber.user.model.User;
import com.team10.uber.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

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
    public User getUserById(@PathVariable Long id, HttpServletRequest request) {
        checkSelfOrAdmin(id, request);
        return userService.getUserById(id);
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User updatedUser, HttpServletRequest request) {
        checkSelfOrAdmin(id, request);
        return userService.updateUser(id, updatedUser);
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Long id, HttpServletRequest request) {
        checkSelfOrAdmin(id, request);
        userService.deleteUser(id);
        return "User deleted successfully";
    }

    private void checkSelfOrAdmin(Long targetId, HttpServletRequest request) {
        Object requesterIdAttr = request.getAttribute("userId");
        Object requesterRoleAttr = request.getAttribute("userRole");
        if (!(requesterIdAttr instanceof Long requesterId) || !(requesterRoleAttr instanceof String requesterRole)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (!requesterId.equals(targetId) && !"ADMIN".equals(requesterRole)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden");
        }
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
    public ResponseEntity<UserRideSummaryDTO> getUserRideSummary(@PathVariable Long id, HttpServletRequest request) {
        log.info("Received {} {}", request.getMethod(), request.getRequestURI());
        checkSelfOrAdmin(id, request);
        ResponseEntity<UserRideSummaryDTO> response = ResponseEntity.ok(userService.getUserRideSummary(id));
        log.info("Returning {} for {} {}", response.getStatusCode(), request.getMethod(), request.getRequestURI());
        return response;
    }

    @GetMapping("/reports/top-riders")
    public ResponseEntity<List<TopRiderDTO>> getTopRiders(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam int limit,
            HttpServletRequest request) {
        log.info("Received {} {}", request.getMethod(), request.getRequestURI());
        ResponseEntity<List<TopRiderDTO>> response = ResponseEntity.ok(userService.getTopRiders(startDate, endDate, limit));
        log.info("Returning {} for {} {}", response.getStatusCode(), request.getMethod(), request.getRequestURI());
        return response;
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
            @RequestParam(defaultValue = "0") int minRides,
            HttpServletRequest request) {
        log.info("Received {} {}", request.getMethod(), request.getRequestURI());
        ResponseEntity<List<User>> response = ResponseEntity.ok(userService.searchByLanguageAndRides(lang, minRides));
        log.info("Returning {} for {} {}", response.getStatusCode(), request.getMethod(), request.getRequestURI());
        return response;
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<User> deactivateUser(@PathVariable Long id, HttpServletRequest request) {
        log.info("Received {} {}", request.getMethod(), request.getRequestURI());
        checkSelfOrAdmin(id, request);
        ResponseEntity<User> response = ResponseEntity.ok(userService.deactivateUser(id));
        log.info("Returning {} for {} {}", response.getStatusCode(), request.getMethod(), request.getRequestURI());
        return response;
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<UserActivityFeedResponseDTO> getUserActivityFeed(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {

        Object requesterIdAttribute = request.getAttribute("userId");
        Object requesterRoleAttribute = request.getAttribute("userRole");

        if (!(requesterIdAttribute instanceof Long requesterId) || !(requesterRoleAttribute instanceof String requesterRole)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        if (!requesterId.equals(id) && !"ADMIN".equals(requesterRole)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden");
        }

        if (page < 0 || size <= 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "page and size must be >= 0, size must be >= 1");
        }

        return ResponseEntity.ok(userService.getUserActivityFeed(id, page, size));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateUserRole(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUserRole(id, request.getRole()));
    }
}
