package com.team10.uber.user.controller;

import com.team10.uber.user.model.SavedAddress;
import com.team10.uber.user.service.SavedAddressService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SavedAddressController {

    private final SavedAddressService savedAddressService;

    public SavedAddressController(SavedAddressService savedAddressService) {
        this.savedAddressService = savedAddressService;
    }

    @PostMapping("/api/addresses/user/{userId}")
    public SavedAddress createSavedAddress(@PathVariable Long userId, @RequestBody SavedAddress savedAddress) {
        return savedAddressService.createSavedAddress(userId, savedAddress);
    }

    @GetMapping("/api/addresses")
    public List<SavedAddress> getAllSavedAddresses() {
        return savedAddressService.getAllSavedAddresses();
    }

    @GetMapping("/api/addresses/{id}")
    public SavedAddress getSavedAddressById(@PathVariable Long id) {
        return savedAddressService.getSavedAddressById(id);
    }

    @PutMapping("/api/addresses/{id}")
    public SavedAddress updateSavedAddress(@PathVariable Long id, @RequestBody SavedAddress updatedAddress) {
        return savedAddressService.updateSavedAddress(id, updatedAddress);
    }

    @DeleteMapping("/api/addresses/{id}")
    public String deleteSavedAddress(@PathVariable Long id) {
        savedAddressService.deleteSavedAddress(id);
        return "Saved address deleted successfully";
    }

    @PostMapping("/api/users/{userId}/addresses")
    public SavedAddress createUserSavedAddress(@PathVariable Long userId, @RequestBody SavedAddress savedAddress) {
        return savedAddressService.createSavedAddress(userId, savedAddress);
    }

    @GetMapping("/api/users/{userId}/addresses")
    public List<SavedAddress> getUserSavedAddresses(@PathVariable Long userId) {
        return savedAddressService.getSavedAddressesByUserId(userId);
    }

    @DeleteMapping("/api/users/{userId}/addresses/{addressId}")
    public String deleteUserSavedAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        savedAddressService.deleteSavedAddress(userId, addressId);
        return "Saved address deleted successfully";
    }

    @PutMapping("/api/users/{userId}/addresses/{addressId}/default")
    public SavedAddress setDefaultAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        return savedAddressService.setDefaultSavedAddress(userId, addressId);
    }
}
