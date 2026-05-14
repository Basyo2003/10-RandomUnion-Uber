package com.team10.uber.user.service;

import com.team10.uber.user.model.SavedAddress;
import com.team10.uber.user.model.User;
import com.team10.uber.user.repository.SavedAddressRepository;
import com.team10.uber.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        User user = findUserByIdOrThrow(userId);
        savedAddress.setUser(user);
        return savedAddressRepository.save(savedAddress);
    }

    public List<SavedAddress> getAllSavedAddresses() {
        return savedAddressRepository.findAll();
    }

    public List<SavedAddress> getSavedAddressesByUserId(Long userId) {
        findUserByIdOrThrow(userId);
        return savedAddressRepository.findByUserId(userId);
    }

    public SavedAddress getSavedAddressById(Long id) {
        return savedAddressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved address not found with id: " + id));
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

    public void deleteSavedAddress(Long userId, Long addressId) {
        findUserByIdOrThrow(userId);
        savedAddressRepository.delete(getSavedAddressForUser(userId, addressId));
    }

    @Transactional
    public SavedAddress setDefaultSavedAddress(Long userId, Long addressId) {
        findUserByIdOrThrow(userId);

        SavedAddress address = savedAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved address not found with id: " + addressId));

        if (!address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address does not belong to the specified user");
        }

        List<SavedAddress> userAddresses = savedAddressRepository.findByUserId(userId);
        for (SavedAddress savedAddress : userAddresses) {
            savedAddress.setIsDefault(savedAddress.getId().equals(addressId));
        }
        savedAddressRepository.saveAll(userAddresses);
        return address;
    }

    private User findUserByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId));
    }

    private SavedAddress getSavedAddressForUser(Long userId, Long addressId) {
        return savedAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved address not found with id: " + addressId));
    }
}
