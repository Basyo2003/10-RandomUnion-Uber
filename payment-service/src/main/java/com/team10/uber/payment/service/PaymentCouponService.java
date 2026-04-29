package com.team10.uber.payment.service;

import com.team10.uber.payment.model.Coupon;
import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.model.PaymentCoupon;
import com.team10.uber.payment.repository.CouponRepository;
import com.team10.uber.payment.repository.PaymentCouponRepository;
import com.team10.uber.payment.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PaymentCouponService {

    private final PaymentRepository paymentRepository;
    private final CouponRepository couponRepository;
    private final PaymentCouponRepository paymentCouponRepository;

    public PaymentCouponService(PaymentRepository paymentRepository,
                                CouponRepository couponRepository,
                                PaymentCouponRepository paymentCouponRepository) {
        this.paymentRepository = paymentRepository;
        this.couponRepository = couponRepository;
        this.paymentCouponRepository = paymentCouponRepository;
    }

    @Caching(evict = {
        @CacheEvict(value = "payment-service::S5-F8", allEntries = true)
    })
    public PaymentCoupon createPaymentCoupon(Long paymentId, Long couponId, PaymentCoupon pc) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));

        pc.setPayment(payment);
        pc.setCoupon(coupon);

        return paymentCouponRepository.save(pc);
    }

    public List<PaymentCoupon> getAllPaymentCoupons() {
        return paymentCouponRepository.findAll();
    }

    @Cacheable(value = "payment-service::payment_coupon", key = "#id")
    public PaymentCoupon getPaymentCouponById(Long id) {
        return paymentCouponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "PaymentCoupon not found with id: " + id));
    }

    @Caching(evict = {
        @CacheEvict(value = "payment-service::payment_coupon", key = "#id"),
        @CacheEvict(value = "payment-service::S5-F8", allEntries = true)
    })
    public PaymentCoupon updatePaymentCoupon(Long id, PaymentCoupon updatedPaymentCoupon) {

        PaymentCoupon existing = getPaymentCouponById(id);
        existing.setDiscountApplied(updatedPaymentCoupon.getDiscountApplied());

        return paymentCouponRepository.save(existing);
    }

    @Caching(evict = {
        @CacheEvict(value = "payment-service::payment_coupon", key = "#id"),
        @CacheEvict(value = "payment-service::S5-F8", allEntries = true)
    })
    public void deletePaymentCoupon(Long id) {
        if (!paymentCouponRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "PaymentCoupon not found with id: " + id);
        }
        paymentCouponRepository.deleteById(id);
    }
}
