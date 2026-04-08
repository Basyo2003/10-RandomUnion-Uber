package com.team10.uber.user.controller;

import com.team10.uber.user.model.SavedAddress;
import com.team10.uber.user.service.SavedAddressService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class SavedAddressController {

    private final SavedAddressService savedAddressService;

    public SavedAddressController(SavedAddressService savedAddressService) {
        this.savedAddressService = savedAddressService;
    }

    @PostMapping("/user/{userId}")
    public SavedAddress createSavedAddress(@PathVariable Long userId, @RequestBody SavedAddress savedAddress) {
        return savedAddressService.createSavedAddress(userId, savedAddress);
    }

    @GetMapping
    public List<SavedAddress> getAllSavedAddresses() {
        return savedAddressService.getAllSavedAddresses();
    }

    @GetMapping("/{id}")
    public SavedAddress getSavedAddressById(@PathVariable Long id) {
        return savedAddressService.getSavedAddressById(id);
    }

    @PutMapping("/{id}")
    public SavedAddress updateSavedAddress(@PathVariable Long id, @RequestBody SavedAddress updatedAddress) {
        return savedAddressService.updateSavedAddress(id, updatedAddress);
    }

    @DeleteMapping("/{id}")
    public String deleteSavedAddress(@PathVariable Long id) {
        savedAddressService.deleteSavedAddress(id);
        return "Saved address deleted successfully";
    }
}