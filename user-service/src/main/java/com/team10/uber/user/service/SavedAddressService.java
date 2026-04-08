package com.team10.uber.user.service;

import com.team10.uber.user.model.SavedAddress;
import com.team10.uber.user.model.User;
import com.team10.uber.user.repository.SavedAddressRepository;
import com.team10.uber.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SavedAddressService {

    private final SavedAddressRepository savedAddressRepository;
    private final UserRepository userRepository;

    public SavedAddressService(SavedAddressRepository savedAddressRepository, UserRepository userRepository) {
        this.savedAddressRepository = savedAddressRepository;
        this.userRepository = userRepository;
    }

    public SavedAddress createSavedAddress(Long userId, SavedAddress savedAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        savedAddress.setUser(user);
        return savedAddressRepository.save(savedAddress);
    }

    public List<SavedAddress> getAllSavedAddresses() {
        return savedAddressRepository.findAll();
    }

    public SavedAddress getSavedAddressById(Long id) {
        return savedAddressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saved address not found with id: " + id));
    }

    public SavedAddress updateSavedAddress(Long id, SavedAddress updatedAddress) {
        SavedAddress existingAddress = getSavedAddressById(id);

        existingAddress.setLabel(updatedAddress.getLabel());
        existingAddress.setAddress(updatedAddress.getAddress());
        existingAddress.setLatitude(updatedAddress.getLatitude());
        existingAddress.setLongitude(updatedAddress.getLongitude());
        existingAddress.setIsDefault(updatedAddress.getIsDefault());
        existingAddress.setMetadata(updatedAddress.getMetadata());

        return savedAddressRepository.save(existingAddress);
    }

    public void deleteSavedAddress(Long id) {
        SavedAddress savedAddress = getSavedAddressById(id);
        savedAddressRepository.delete(savedAddress);
    }
}