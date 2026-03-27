package com.team10.uber.user.service;

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
}