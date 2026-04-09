package com.team10.uber.driver.dto;

public class VerifyDriverDTO {
    private Long verifiedBy;

    public VerifyDriverDTO() {
    }

    public VerifyDriverDTO(Long verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public Long getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Long verifiedBy) {
        this.verifiedBy = verifiedBy;
    }
}
