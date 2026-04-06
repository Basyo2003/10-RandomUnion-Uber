package com.team10.uber.user.service;

import com.team10.uber.user.dto.AddressDTO;
import com.team10.uber.user.dto.UserProfileDTO;
import com.team10.uber.user.enums.Status;
import com.team10.uber.user.model.SavedAddress;
import com.team10.uber.user.model.User;
import com.team10.uber.user.repository.SavedAddressRepository;
import com.team10.uber.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SavedAddressRepository savedAddressRepository;

    public UserService(UserRepository userRepository, SavedAddressRepository savedAddressRepository) {
        this.userRepository = userRepository;
        this.savedAddressRepository = savedAddressRepository;
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

    @Transactional
    public User deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (userRepository.hasActiveRides(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has active rides");
        }

        user.setStatus(Status.DEACTIVATED);
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
    @Transactional
    public UserProfileDTO setDefaultSavedAddress(Long userId, Long addressId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

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
}
