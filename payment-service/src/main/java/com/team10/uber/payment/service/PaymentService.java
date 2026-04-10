package com.team10.uber.payment.service;

import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.team10.uber.payment.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import com.team10.uber.payment.dto.UserPaymentSummaryDTO;
import com.team10.uber.payment.dto.ProcessPaymentDTO;
import com.team10.uber.payment.enums.PaymentMethod;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import com.team10.uber.payment.enums.PaymentStatus;
import java.util.Map;
import java.util.HashMap;
import com.team10.uber.payment.model.Coupon;
import com.team10.uber.payment.model.PaymentCoupon;
import com.team10.uber.payment.repository.CouponRepository;
import com.team10.uber.payment.repository.PaymentCouponRepository;
import com.team10.uber.payment.enums.DiscountType;
import java.time.LocalDateTime;


import com.team10.uber.payment.dto.CouponUsageDTO;
import com.team10.uber.payment.dto.PaymentDetailsDTO;
import com.team10.uber.payment.dto.RevenueReportDTO;

import java.time.LocalDateTime;

import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CouponRepository couponRepository;
    private final PaymentCouponRepository paymentCouponRepository;

    public PaymentService(PaymentRepository paymentRepository,
            CouponRepository couponRepository,
            PaymentCouponRepository paymentCouponRepository) {
        this.paymentRepository = paymentRepository;
        this.couponRepository = couponRepository;
        this.paymentCouponRepository = paymentCouponRepository;
    }

    public Payment createPayment(Payment payment) {

        // Check user exists
        if (paymentRepository.countUserById(payment.getUserId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User not found with id: " + payment.getUserId());
        }

        // Check ride exists
        if (paymentRepository.countRideById(payment.getRideId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ride not found with id: " + payment.getRideId());
        }

        // Check ride does not already have a payment
        if (paymentRepository.countPaymentsByRideId(payment.getRideId()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A payment already exists for ride id: " + payment.getRideId());
        }

        return paymentRepository.save(payment);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
    }

    public Payment updatePayment(Long id, Payment updatedPayment) {
        Payment existing = getPaymentById(id);
        existing.setRideId(updatedPayment.getRideId());
        existing.setUserId(updatedPayment.getUserId());
        existing.setAmount(updatedPayment.getAmount());
        existing.setMethod(updatedPayment.getMethod());
        existing.setStatus(updatedPayment.getStatus());
        existing.setTransactionDetails(updatedPayment.getTransactionDetails());
        return paymentRepository.save(existing);
    }

    public void deletePayment(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Payment not found with id: " + id);
        }
        paymentRepository.deleteById(id);
    }

    @Transactional
    public Payment refundPayment(Long id, String reason) {
        Payment payment = getPaymentById(id);

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Payment cannot be refunded. Current status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);

        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) {
            details = new HashMap<>();
        }
        details.put("refundReason", reason);
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);

        return paymentRepository.save(payment);
    }

    public UserPaymentSummaryDTO getUserPaymentSummary(Long userId) {
        // a) Verify user exists via native SQL
        int userCount = paymentRepository.countUserById(userId);
        if (userCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
        }

        // b) Query payment data grouped by method
        List<Object[]> results = paymentRepository.findPaymentSummaryByUserId(userId);

        // c) Build method breakdown map
        Map<String, Double> methodBreakdown = new HashMap<>();
        for (Object[] row : results) {
            String method = (String) row[0];
            Double totalForMethod = ((Number) row[2]).doubleValue();
            methodBreakdown.put(method, totalForMethod);
        }

        // d) Calculate totals
        long totalPayments = 0;
        double totalAmount = 0.0;
        for (Object[] row : results) {
            totalPayments += ((Number) row[1]).longValue();
            totalAmount += ((Number) row[2]).doubleValue();
        }

        // e) Build and return DTO
        return new UserPaymentSummaryDTO(userId, totalPayments, totalAmount, methodBreakdown);
    }

    @Transactional
    public Payment processPaymentForRide(Long rideId, ProcessPaymentDTO dto) {

        // a) Verify ride exists
        String rideStatus = paymentRepository.findRideStatusById(rideId);
        if (rideStatus == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId);
        }

        // b) Verify ride is COMPLETED
        if (!rideStatus.equals("COMPLETED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride is not completed. Current status: " + rideStatus);
        }

        // c) Check no COMPLETED payment exists for this ride
        Optional<Payment> existingPayment = paymentRepository.findByRideIdAndStatus(rideId, PaymentStatus.COMPLETED);
        if (existingPayment.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
        }

        // d) Validate cardLastFour
        if (dto.getMethod().equals("CREDIT_CARD")) {
            String cardLastFour = dto.getCardLastFour();
            if (cardLastFour == null || cardLastFour.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cardLastFour is required for CREDIT_CARD payments");
            }
            if (cardLastFour.length() != 4 || !cardLastFour.matches("\\d{4}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cardLastFour must be exactly 4 digits");
            }
        } else {
            if (dto.getCardLastFour() != null && !dto.getCardLastFour().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cardLastFour should not be provided for " + dto.getMethod() + " payments");
            }
        }

        // e) Get ride details
        Double fare = paymentRepository.findRideFareById(rideId);
        Long userId = paymentRepository.findRideUserIdById(rideId);

        // f) Build JSONB transactionDetails
        Map<String, Object> transactionDetails = new HashMap<>();
        transactionDetails.put("gatewayResponse", "success");
        transactionDetails.put("cardLastFour", dto.getCardLastFour());
        transactionDetails.put("receiptUrl", "https://receipts.com/" + rideId);
        transactionDetails.put("failureReason", null);

        // g) Create and save payment
        Payment payment = new Payment();
        payment.setRideId(rideId);
        payment.setUserId(userId);
        payment.setAmount(fare != null ? fare : 0.0);
        payment.setMethod(PaymentMethod.valueOf(dto.getMethod()));
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionDetails(transactionDetails);

        return paymentRepository.save(payment);
    }
    public PaymentDetailsDTO applyCoupon(Long paymentId, Long couponId) {
        Payment payment = getPaymentById(paymentId);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot apply coupon to a completed/cancelled payment");
        }

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Coupon not found with id: " + couponId));

        if (!coupon.getActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon is not active");
        }
        if (coupon.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon has expired");
        }
        if (coupon.getCurrentUses() >= coupon.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon usage limit reached");
        }

        Optional<PaymentCoupon> existing = paymentCouponRepository
                .findByPaymentIdAndCouponId(paymentId, couponId);
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coupon already applied");
        }

        double discount;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = payment.getAmount() * coupon.getDiscountValue() / 100;
        } else {
            discount = coupon.getDiscountValue();
        }
        if (discount > payment.getAmount()) {
            discount = payment.getAmount();
        }

        PaymentCoupon paymentCoupon = new PaymentCoupon();
        paymentCoupon.setPayment(payment);
        paymentCoupon.setCoupon(coupon);
        paymentCoupon.setDiscountApplied(discount);

        coupon.setCurrentUses(coupon.getCurrentUses() + 1);

        paymentCouponRepository.save(paymentCoupon);
        couponRepository.save(coupon);

        return getPaymentDetails(paymentId);
    }

    public List<Payment> searchPayments(String status, LocalDateTime startDate, LocalDateTime endDate) {
        return paymentRepository.findByStatusAndDateRange(status, startDate, endDate);
    }

    public RevenueReportDTO getRevenueReport(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }

        Object[] revenueRow = paymentRepository.findRevenueStats(startDate, endDate);
        Object[] refundRow  = paymentRepository.findRefundStats(startDate, endDate);

        if (revenueRow != null && revenueRow.length > 0 && revenueRow[0] instanceof Object[]) {
            revenueRow = (Object[]) revenueRow[0];
        }
        if (refundRow != null && refundRow.length > 0 && refundRow[0] instanceof Object[]) {
            refundRow = (Object[]) refundRow[0];
        }

        Double totalRevenue = (revenueRow != null && revenueRow.length > 0 && revenueRow[0] != null) ? ((Number) revenueRow[0]).doubleValue() : 0.0;
        Long   totalTransactions = (revenueRow != null && revenueRow.length > 1 && revenueRow[1] != null) ? ((Number) revenueRow[1]).longValue() : 0L;
        Double averagePayment= (totalTransactions > 0) ? totalRevenue / totalTransactions : 0.0;

        Double refundedAmount = (refundRow != null && refundRow.length > 0 && refundRow[0] != null) ? ((Number) refundRow[0]).doubleValue() : 0.0;
        Long   refundCount = (refundRow != null && refundRow.length > 1 && refundRow[1] != null) ? ((Number) refundRow[1]).longValue() : 0L;

        return new RevenueReportDTO(totalRevenue, totalTransactions, averagePayment, refundedAmount, refundCount);
    }

    @Transactional
    public Payment retryPayment(Long id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only FAILED payments can be retried");
        }
    
        payment.setStatus(PaymentStatus.COMPLETED);
    
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) {
            details = new java.util.HashMap<>();
        }
    
        int retryAttempt = details.containsKey("retryAttempt") ? ((Number) details.get("retryAttempt")).intValue() + 1 : 1;
    
        details.put("retryAttempt", retryAttempt);
        details.put("gatewayResponse", "approved");
        payment.setTransactionDetails(details);
    
        return paymentRepository.save(payment);
    }

    public PaymentDetailsDTO getPaymentDetails(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        List<PaymentCoupon> paymentCoupons = paymentCouponRepository.findByPaymentId(paymentId);

        List<PaymentDetailsDTO.AppliedCouponDTO> appliedCouponDTOs = paymentCoupons.stream().map(pc -> new PaymentDetailsDTO.AppliedCouponDTO(pc.getCoupon().getCode(), pc.getCoupon().getDiscountType().name(), pc.getDiscountApplied(), pc.getAppliedAt())).collect(java.util.stream.Collectors.toList());

        Double totalDiscount = appliedCouponDTOs.stream().mapToDouble(PaymentDetailsDTO.AppliedCouponDTO::getDiscountApplied).sum();

        Double finalAmount = payment.getAmount() - totalDiscount;

        Map<String, Object> transactionDetails = payment.getTransactionDetails();
        if (transactionDetails == null) {
            transactionDetails = new java.util.HashMap<>();
        }

        return new PaymentDetailsDTO(payment.getId(), payment.getRideId(), payment.getUserId(), payment.getAmount(), payment.getMethod(), payment.getStatus(), transactionDetails, appliedCouponDTOs, totalDiscount, finalAmount); 
    }

    public List<CouponUsageDTO> getTopUsedCoupons(int limit) {
        List<Object[]> rows = couponRepository.findTopUsedCoupons(limit);

        return rows.stream().map(row -> {
            Long couponId = ((Number) row[0]).longValue();
            String code = (String) row[1];
            String discountType = (String) row[2];
            Double discountValue = ((Number) row[3]).doubleValue();
            Integer timesUsed = ((Number) row[4]).intValue();
            Double totalDiscountGiven = ((Number) row[5]).doubleValue();
            Boolean active = (Boolean) row[6];

            java.time.LocalDateTime expiryDate;
            if (row[7] instanceof java.sql.Timestamp) {
                expiryDate = ((java.sql.Timestamp) row[7]).toLocalDateTime();
            } else if (row[7] instanceof java.time.LocalDateTime) {
                expiryDate = (java.time.LocalDateTime) row[7];
            } else {
                expiryDate = java.time.LocalDateTime.parse(row[7].toString());
            }
            Boolean expired = expiryDate.isBefore(java.time.LocalDateTime.now());

            return new CouponUsageDTO(couponId, code, discountType, discountValue, timesUsed, totalDiscountGiven, active, expired);
        }).collect(java.util.stream.Collectors.toList());
    }
}