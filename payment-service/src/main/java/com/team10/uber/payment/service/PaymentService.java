package com.team10.uber.payment.service;

import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.observer.AbstractObservableService;
import com.team10.uber.payment.observer.MongoEventLogger;
import com.team10.uber.payment.repository.PaymentRepository;
import jakarta.annotation.PostConstruct;
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
import com.team10.uber.payment.strategy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.team10.uber.payment.dto.CouponUsageDTO;
import com.team10.uber.payment.dto.PaymentDetailsDTO;
import com.team10.uber.payment.dto.RevenueReportDTO;

import java.time.LocalDateTime;

import java.util.List;

@Service
public class PaymentService extends AbstractObservableService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository paymentRepository;
    private final CouponRepository couponRepository;
    private final PaymentCouponRepository paymentCouponRepository;
    private final MongoEventLogger mongoEventLogger;

    public PaymentService(PaymentRepository paymentRepository,
            CouponRepository couponRepository,
            PaymentCouponRepository paymentCouponRepository,
            MongoEventLogger mongoEventLogger) {
        this.paymentRepository = paymentRepository;
        this.couponRepository = couponRepository;
        this.paymentCouponRepository = paymentCouponRepository;
        this.mongoEventLogger = mongoEventLogger;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
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

        Payment saved = paymentRepository.save(payment);
        notifyObservers("CREATED", saved);
        return saved;
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
        Payment saved = paymentRepository.save(existing);
        notifyObservers("UPDATED", saved);
        return saved;
    }

    public void deletePayment(Long id) {
        Payment payment = getPaymentById(id);
        paymentRepository.deleteById(id);
        notifyObservers("PAYMENT_DELETED", payment);
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

        Payment saved = paymentRepository.save(payment);
        notifyObservers("REFUNDED", saved);
        return saved;
    }

    public LocalDateTime parseDate(String date, boolean endOfDay) {
        if (date == null) return null;
        if (date.contains("T")) {
            return LocalDateTime.parse(date);
        }
        return endOfDay
                ? LocalDateTime.parse(date + "T23:59:59.999999999")
                : LocalDateTime.parse(date + "T00:00:00");
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
        if (dto.getMethod().equals("CREDIT_CARD") && dto.getCardLastFour() != null) {
            String cardLastFour = dto.getCardLastFour();
            if (cardLastFour.length() != 4 || !cardLastFour.matches("\\d{4}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cardLastFour must be exactly 4 digits");
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

        Payment saved = paymentRepository.save(payment);
        notifyObservers("COMPLETED", saved);
        return saved;
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

        notifyObservers("COUPON_APPLIED", payment);
        return getPaymentDetails(paymentId);
    }

    public List<Payment> searchPayments(String status, LocalDateTime startDate, LocalDateTime endDate) {
        List<Payment> result;
        if (status == null || status.isEmpty()) {
            result = paymentRepository.findByDateRange(startDate, endDate);
        } else {
            result = paymentRepository.findByStatusAndDateRange(status, startDate, endDate);
        }

        System.out.println("Result size: " + result.size());
        return result;
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

        Payment saved = paymentRepository.save(payment);
        notifyObservers("RETRY_ATTEMPTED", saved);
        return saved;
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

    @Transactional
    public Payment refundSurgeAdjusted(Long id, RefundRequest request) {

        // §10.5.3b — Find payment → 404
        Payment payment = getPaymentById(id);

        // §10.5.3c — Validate status == COMPLETED → 400
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment must be COMPLETED to be refunded");
        }

        // §3.2 — Select strategy via selector — NO if/else here
        RefundStrategySelector selector = new RefundStrategySelector();
        RefundStrategy strategy = selector.select(payment, request);
        RefundResult result = strategy.calculateRefund(payment, request);

        // §10.5.3e — If NoRefundStrategy selected
        if (result.getAmount() == 0.0 && result.getReasonCode().equals("refund window expired")) {

            // Log REFUND_DENIED to MongoDB
            logPaymentEvent(payment, "REFUND_DENIED", Map.of(
                    "strategyName", result.getStrategyName(),
                    "reason", result.getReasonCode()
            ));

            // §10.5.3e — Invalidate caches
            evictAnalyticsCaches();

            // §10.5.3e — Throw 400 after logging
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refund window expired");
        }

        // §10.5.3f — Update payment status
        payment.setStatus(PaymentStatus.REFUNDED);

        // §10.5.3g — Update transactionDetails JSONB
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundAmount", result.getAmount());
        details.put("refundSurgeIncluded", request.isRefundSurge());
        details.put("refundReason", request.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);

        Payment saved = paymentRepository.save(payment);

        // §10.5.3h — Log REFUNDED event to MongoDB
        logPaymentEvent(payment, "REFUNDED", Map.of(
                "strategyName", result.getStrategyName(),
                "reason", request.getReason(),
                "originalAmount", payment.getAmount(),
                "refundAmount", result.getAmount(),
                "surgeIncluded", request.isRefundSurge()
        ));

        // §10.5.3i — Invalidate caches
        evictAnalyticsCaches();

        return saved;
    }

    private void logPaymentEvent(Payment payment, String action, Map<String, Object> details) {
        try {
            // Log to MongoDB payment_audit_trail via Observer
            // (wire to your existing MongoEventLogger/Observer pattern)
        } catch (Exception e) {
            log.warn("MongoDB write failed: {}", e.getMessage());
        }
    }

    private void evictAnalyticsCaches() {
        // Invalidate S5-F10 and S5-F11 caches
        // Use your Redis template or cache manager
    }
}