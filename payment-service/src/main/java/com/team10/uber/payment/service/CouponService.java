package com.team10.uber.payment.service;

import com.team10.uber.payment.model.Coupon;
import com.team10.uber.payment.repository.CouponRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @CacheEvict(value = "payment-service::S5-F9", allEntries = true)
    public Coupon createCoupon(Coupon coupon) {
        return couponRepository.save(coupon);
    }

    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }
    @Cacheable(value = "payment-service::coupon", key = "#id")
    public Coupon getCouponById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Coupon not found with id: " + id));
    }

    @Caching(evict = {
        @CacheEvict(value = "payment-service::coupon", key = "#id"),
        @CacheEvict(value = "payment-service::S5-F9", allEntries = true),
        @CacheEvict(value = "payment-service::S5-F8", allEntries = true)
    })
    public Coupon updateCoupon(Long id, Coupon updatedCoupon) {
        Coupon existing = getCouponById(id);
        existing.setCode(updatedCoupon.getCode());
        existing.setDiscountType(updatedCoupon.getDiscountType());
        existing.setDiscountValue(updatedCoupon.getDiscountValue());
        existing.setMaxUses(updatedCoupon.getMaxUses());
        existing.setCurrentUses(updatedCoupon.getCurrentUses());
        existing.setExpiryDate(updatedCoupon.getExpiryDate());
        existing.setActive(updatedCoupon.getActive());
        existing.setMetadata(updatedCoupon.getMetadata());
        return couponRepository.save(existing);
    }

    @Caching(evict = {
        @CacheEvict(value = "payment-service::coupon", key = "#id"),
        @CacheEvict(value = "payment-service::S5-F9", allEntries = true),
        @CacheEvict(value = "payment-service::S5-F8", allEntries = true)
    })
    public void deleteCoupon(Long id) {
        if (!couponRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Coupon not found with id: " + id);
        }
        couponRepository.deleteById(id);
    }
}