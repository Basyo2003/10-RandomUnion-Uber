package com.team10.uber.payment.dto;

public class CouponUsageDTO {

    private Long couponId;
    private String code;
    private String discountType;
    private Double discountValue;
    private Integer timesUsed;
    private Double totalDiscountGiven;
    private Boolean active;
    private Boolean expired;

    public CouponUsageDTO() {}

    public CouponUsageDTO(Long couponId, String code, String discountType, Double discountValue,
                          Integer timesUsed, Double totalDiscountGiven, Boolean active, Boolean expired) {
        this.couponId = couponId;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.timesUsed = timesUsed;
        this.totalDiscountGiven = totalDiscountGiven;
        this.active = active;
        this.expired = expired;
    }

    // §3.5 — Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long couponId;
        private String code;
        private String discountType;
        private Double discountValue;
        private Integer timesUsed;
        private Double totalDiscountGiven;
        private Boolean active;
        private Boolean expired;

        public Builder couponId(Long couponId) { this.couponId = couponId; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder discountType(String discountType) { this.discountType = discountType; return this; }
        public Builder discountValue(Double discountValue) { this.discountValue = discountValue; return this; }
        public Builder timesUsed(Integer timesUsed) { this.timesUsed = timesUsed; return this; }
        public Builder totalDiscountGiven(Double totalDiscountGiven) { this.totalDiscountGiven = totalDiscountGiven; return this; }
        public Builder active(Boolean active) { this.active = active; return this; }
        public Builder expired(Boolean expired) { this.expired = expired; return this; }

        public CouponUsageDTO build() {
            return new CouponUsageDTO(couponId, code, discountType, discountValue,
                    timesUsed, totalDiscountGiven, active, expired);
        }
    }

    public Long getCouponId() { return couponId; }
    public String getCode() { return code; }
    public String getDiscountType() { return discountType; }
    public Double getDiscountValue() { return discountValue; }
    public Integer getTimesUsed() { return timesUsed; }
    public Double getTotalDiscountGiven() { return totalDiscountGiven; }
    public Boolean getActive() { return active; }
    public Boolean getExpired() { return expired; }
}