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

    public CouponUsageDTO(Long couponId, String code, String discountType, Double discountValue, Integer timesUsed, Double totalDiscountGiven, Boolean active, Boolean expired) {
        this.couponId = couponId;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.timesUsed = timesUsed;
        this.totalDiscountGiven = totalDiscountGiven;
        this.active = active;
        this.expired = expired;
    }

    public Long getCouponId() {
        return couponId;
    }

    public String getCode() {
        return code;
    }

    public String getDiscountType() {
        return discountType;
    }

    public Double getDiscountValue() {
        return discountValue;
    }

    public Integer getTimesUsed() {
        return timesUsed;
    }

    public Double getTotalDiscountGiven() {
        return totalDiscountGiven;
    }

    public Boolean getActive() {
        return active;
    }

    public Boolean getExpired() {
        return expired;
    }
}