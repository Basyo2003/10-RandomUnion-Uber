package com.team10.uber.user.dto;

import java.util.List;
import java.util.Map;

public class UserProfileDTO {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Map<String, Object> preferences;
    private List<AddressDTO> savedAddresses;
    private Integer totalAddresses;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, Object> preferences) {
        this.preferences = preferences;
    }

    public List<AddressDTO> getSavedAddresses() {
        return savedAddresses;
    }

    public void setSavedAddresses(List<AddressDTO> savedAddresses) {
        this.savedAddresses = savedAddresses;
    }

    public Integer getTotalAddresses() {
        return totalAddresses;
    }

    public void setTotalAddresses(Integer totalAddresses) {
        this.totalAddresses = totalAddresses;
    }
}
