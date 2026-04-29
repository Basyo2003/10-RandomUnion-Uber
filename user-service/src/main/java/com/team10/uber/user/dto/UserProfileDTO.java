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

    public UserProfileDTO() {}

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long userId;
        private String name;
        private String email;
        private String phone;
        private Map<String, Object> preferences;
        private List<AddressDTO> savedAddresses;
        private Integer totalAddresses;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder phone(String phone) { this.phone = phone; return this; }
        public Builder preferences(Map<String, Object> preferences) { this.preferences = preferences; return this; }
        public Builder savedAddresses(List<AddressDTO> savedAddresses) { this.savedAddresses = savedAddresses; return this; }
        public Builder totalAddresses(Integer totalAddresses) { this.totalAddresses = totalAddresses; return this; }

        public UserProfileDTO build() {
            UserProfileDTO dto = new UserProfileDTO();
            dto.userId = this.userId;
            dto.name = this.name;
            dto.email = this.email;
            dto.phone = this.phone;
            dto.preferences = this.preferences;
            dto.savedAddresses = this.savedAddresses;
            dto.totalAddresses = this.totalAddresses;
            return dto;
        }
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }
    public List<AddressDTO> getSavedAddresses() { return savedAddresses; }
    public void setSavedAddresses(List<AddressDTO> savedAddresses) { this.savedAddresses = savedAddresses; }
    public Integer getTotalAddresses() { return totalAddresses; }
    public void setTotalAddresses(Integer totalAddresses) { this.totalAddresses = totalAddresses; }
}