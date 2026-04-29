package com.team10.uber.payment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team10.uber.payment.cache.RedisCacheService;
import com.team10.uber.payment.dto.*;
import com.team10.uber.payment.enums.DiscountType;
import com.team10.uber.payment.enums.PaymentMethod;
import com.team10.uber.payment.enums.PaymentStatus;
import com.team10.uber.payment.event.PaymentAuditEvent;
import com.team10.uber.payment.model.Coupon;
import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.model.PaymentCoupon;
import com.team10.uber.payment.observer.AbstractObservableService;
import com.team10.uber.payment.observer.MongoEventLogger;
import com.team10.uber.payment.repository.CouponRepository;
import com.team10.uber.payment.repository.PaymentCouponRepository;
import com.team10.uber.payment.repository.PaymentRepository;
import com.team10.uber.payment.repository.mongo.PaymentAuditEventRepository;
import com.team10.uber.payment.strategy.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaymentService extends AbstractObservableService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // §8.1 TTL constants
    private static final long TTL_ANALYTICS  = 10 * 60L; // 10 min — dashboards/analytics
    private static final long TTL_SEARCH     =  5 * 60L; // 5 min  — search results
    private static final long TTL_DETAIL     = 15 * 60L; // 15 min — entity detail views

    private final PaymentRepository paymentRepository;
    private final CouponRepository couponRepository;
    private final PaymentCouponRepository paymentCouponRepository;
    private final MongoEventLogger mongoEventLogger;
    private final PaymentAuditEventRepository auditEventRepository;
    private final RedisCacheService cache;

    public PaymentService(PaymentRepository paymentRepository,
                          CouponRepository couponRepository,
                          PaymentCouponRepository paymentCouponRepository,
                          MongoEventLogger mongoEventLogger,
                          PaymentAuditEventRepository auditEventRepository,
                          RedisCacheService cache) {
        this.paymentRepository = paymentRepository;
        this.couponRepository = couponRepository;
        this.paymentCouponRepository = paymentCouponRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.auditEventRepository = auditEventRepository;
        this.cache = cache;
    }

    @PostConstruct
    public void initObservers() {
        register(mongoEventLogger);
    }

    // ── §4.4.6 — Cache invalidation helpers ──────────────────────────────────

    private void evictPaymentCaches(Long paymentId) {
        // entity detail
        cache.evict("payment::" + paymentId);
        // feature caches that reference this payment
        cache.evictByPattern("S5-F1::*");
        cache.evictByPattern("S5-F3::*");
        cache.evictByPattern("S5-F8::" + paymentId);
        cache.evictByPattern("S5-F9::*");
    }

    private void evictAnalyticsCaches() {
        // §10.5.3e / §10.5.3i — invalidate analytics dashboards
        cache.evictByPattern("S5-F6::*");
        cache.evictByPattern("S5-F10::*");
        cache.evictByPattern("S5-F11::*");
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public Payment createPayment(Payment payment) {
        if (paymentRepository.countUserById(payment.getUserId()) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User not found with id: " + payment.getUserId());
        if (paymentRepository.countRideById(payment.getRideId()) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ride not found with id: " + payment.getRideId());
        if (paymentRepository.countPaymentsByRideId(payment.getRideId()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A payment already exists for ride id: " + payment.getRideId());

        Payment saved = paymentRepository.save(payment);
        notifyObservers("CREATED", saved);
        // §4.4.4 — invalidate search/list caches on new payment
        cache.evictByPattern("S5-F1::*");
        cache.evictByPattern("S5-F3::" + saved.getUserId() + "::*");
        evictAnalyticsCaches();
        return saved;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    // §4.4.2 — GET /api/payments/{id}, cached 15 min
    public Payment getPaymentById(Long id) {
        String key = "payment::" + id;
        Payment cached = cache.get(key, Payment.class);
        if (cached != null) return cached;

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
        cache.put(key, payment, TTL_DETAIL);
        return payment;
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
        evictPaymentCaches(id);
        evictAnalyticsCaches();
        return saved;
    }

    public void deletePayment(Long id) {
        Payment payment = getPaymentById(id);
        paymentRepository.deleteById(id);
        notifyObservers("PAYMENT_DELETED", payment);
        evictPaymentCaches(id);
        evictAnalyticsCaches();
    }

    // ── M1 Features ──────────────────────────────────────────────────────────

    // §4.4.1 — S5-F1: search payments, cached 5 min
    public List<Payment> searchPayments(String status, LocalDateTime startDate, LocalDateTime endDate) {
        String key = "S5-F1::" + status + "::" + startDate + "::" + endDate;
        List<Payment> cached = cache.get(key, new TypeReference<List<Payment>>() {});
        if (cached != null) return cached;

        List<Payment> result;
        if (status == null || status.isEmpty()) {
            result = paymentRepository.findByDateRange(startDate, endDate);
        } else {
            result = paymentRepository.findByStatusAndDateRange(status, startDate, endDate);
        }
        cache.put(key, result, TTL_SEARCH);
        return result;
    }

    // §4.4.1 — S5-F2: refund payment — write, invalidates caches
    @Transactional
    public Payment refundPayment(Long id, String reason) {
        Payment payment = getPaymentById(id);
        if (payment.getStatus() != PaymentStatus.COMPLETED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment cannot be refunded. Current status: " + payment.getStatus());

        payment.setStatus(PaymentStatus.REFUNDED);
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundReason", reason);
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);

        Payment saved = paymentRepository.save(payment);
        notifyObservers("REFUNDED", saved);
        evictPaymentCaches(id);
        evictAnalyticsCaches();
        return saved;
    }

    // §4.4.1 — S5-F3: user payment summary, cached 5 min
    public UserPaymentSummaryDTO getUserPaymentSummary(Long userId) {
        String key = "S5-F3::" + userId;
        UserPaymentSummaryDTO cached = cache.get(key, UserPaymentSummaryDTO.class);
        if (cached != null) return cached;

        int userCount = paymentRepository.countUserById(userId);
        if (userCount == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User not found with id: " + userId);

        List<Object[]> results = paymentRepository.findPaymentSummaryByUserId(userId);
        Map<String, Double> methodBreakdown = new HashMap<>();
        long totalPayments = 0;
        double totalAmount = 0.0;

        for (Object[] row : results) {
            methodBreakdown.put((String) row[0], ((Number) row[2]).doubleValue());
            totalPayments += ((Number) row[1]).longValue();
            totalAmount += ((Number) row[2]).doubleValue();
        }

        UserPaymentSummaryDTO result = new UserPaymentSummaryDTO(
                userId, totalPayments, totalAmount, methodBreakdown);
        cache.put(key, result, TTL_SEARCH);
        return result;
    }

    // §4.4.1 — S5-F4: process payment — write, invalidates caches
    @Transactional
    public Payment processPaymentForRide(Long rideId, ProcessPaymentDTO dto) {
        String rideStatus = paymentRepository.findRideStatusById(rideId);
        if (rideStatus == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ride not found with id: " + rideId);
        if (!rideStatus.equals("COMPLETED"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride is not completed. Current status: " + rideStatus);

        Optional<Payment> existingPayment = paymentRepository
                .findByRideIdAndStatus(rideId, PaymentStatus.COMPLETED);
        if (existingPayment.isPresent())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");

        if (dto.getMethod().equals("CREDIT_CARD") && dto.getCardLastFour() != null) {
            String card = dto.getCardLastFour();
            if (card.length() != 4 || !card.matches("\\d{4}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cardLastFour must be exactly 4 digits");
        }

        Double fare = paymentRepository.findRideFareById(rideId);
        Long userId = paymentRepository.findRideUserIdById(rideId);

        Map<String, Object> transactionDetails = new HashMap<>();
        transactionDetails.put("gatewayResponse", "success");
        transactionDetails.put("cardLastFour", dto.getCardLastFour());
        transactionDetails.put("receiptUrl", "https://receipts.com/" + rideId);
        transactionDetails.put("failureReason", null);

        Payment payment = new Payment();
        payment.setRideId(rideId);
        payment.setUserId(userId);
        payment.setAmount(fare != null ? fare : 0.0);
        payment.setMethod(PaymentMethod.valueOf(dto.getMethod()));
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionDetails(transactionDetails);

        Payment saved = paymentRepository.save(payment);
        notifyObservers("COMPLETED", saved);

        // §4.4.4 — invalidate affected caches
        cache.evictByPattern("S5-F1::*");
        cache.evictByPattern("S5-F3::" + userId);
        evictAnalyticsCaches();
        return saved;
    }

    // §4.4.1 — S5-F5: apply coupon — write, invalidates caches
    public PaymentDetailsDTO applyCoupon(Long paymentId, Long couponId) {
        Payment payment = getPaymentById(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot apply coupon to a completed/cancelled payment");

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Coupon not found with id: " + couponId));

        if (!coupon.getActive())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon is not active");
        if (coupon.getExpiryDate().isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon has expired");
        if (coupon.getCurrentUses() >= coupon.getMaxUses())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon usage limit reached");

        Optional<PaymentCoupon> existing = paymentCouponRepository
                .findByPaymentIdAndCouponId(paymentId, couponId);
        if (existing.isPresent())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coupon already applied");

        double discount = coupon.getDiscountType() == DiscountType.PERCENTAGE
                ? payment.getAmount() * coupon.getDiscountValue() / 100
                : coupon.getDiscountValue();
        if (discount > payment.getAmount()) discount = payment.getAmount();

        PaymentCoupon paymentCoupon = new PaymentCoupon();
        paymentCoupon.setPayment(payment);
        paymentCoupon.setCoupon(coupon);
        paymentCoupon.setDiscountApplied(discount);
        coupon.setCurrentUses(coupon.getCurrentUses() + 1);

        paymentCouponRepository.save(paymentCoupon);
        couponRepository.save(coupon);
        notifyObservers("COUPON_APPLIED", payment);

        // §4.4.4 — invalidate detail and coupon caches
        evictPaymentCaches(paymentId);
        cache.evictByPattern("S5-F9::*");
        return getPaymentDetails(paymentId);
    }

    // §4.4.1 — S5-F6: revenue report, cached 10 min
    public RevenueReportDTO getRevenueReport(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be before endDate");

        String key = "S5-F6::" + startDate + "::" + endDate;
        RevenueReportDTO cached = cache.get(key, RevenueReportDTO.class);
        if (cached != null) return cached;

        Object[] revenueRow = paymentRepository.findRevenueStats(startDate, endDate);
        Object[] refundRow  = paymentRepository.findRefundStats(startDate, endDate);

        if (revenueRow != null && revenueRow.length > 0 && revenueRow[0] instanceof Object[])
            revenueRow = (Object[]) revenueRow[0];
        if (refundRow != null && refundRow.length > 0 && refundRow[0] instanceof Object[])
            refundRow = (Object[]) refundRow[0];

        Double totalRevenue = (revenueRow != null && revenueRow.length > 0 && revenueRow[0] != null)
                ? ((Number) revenueRow[0]).doubleValue() : 0.0;
        Long totalTransactions = (revenueRow != null && revenueRow.length > 1 && revenueRow[1] != null)
                ? ((Number) revenueRow[1]).longValue() : 0L;
        Double averagePayment = totalTransactions > 0 ? totalRevenue / totalTransactions : 0.0;
        Double refundedAmount = (refundRow != null && refundRow.length > 0 && refundRow[0] != null)
                ? ((Number) refundRow[0]).doubleValue() : 0.0;
        Long refundCount = (refundRow != null && refundRow.length > 1 && refundRow[1] != null)
                ? ((Number) refundRow[1]).longValue() : 0L;

        RevenueReportDTO result = new RevenueReportDTO(
                totalRevenue, totalTransactions, averagePayment, refundedAmount, refundCount);
        cache.put(key, result, TTL_ANALYTICS);
        return result;
    }

    // §4.4.1 — S5-F7: retry payment — write, invalidates caches
    @Transactional
    public Payment retryPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() != PaymentStatus.FAILED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only FAILED payments can be retried");

        payment.setStatus(PaymentStatus.COMPLETED);
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        int retryAttempt = details.containsKey("retryAttempt")
                ? ((Number) details.get("retryAttempt")).intValue() + 1 : 1;
        details.put("retryAttempt", retryAttempt);
        details.put("gatewayResponse", "approved");
        payment.setTransactionDetails(details);

        Payment saved = paymentRepository.save(payment);
        notifyObservers("RETRY_ATTEMPTED", saved);
        evictPaymentCaches(id);
        evictAnalyticsCaches();
        return saved;
    }

    // §4.4.1 — S5-F8: payment details, cached 15 min
    public PaymentDetailsDTO getPaymentDetails(Long paymentId) {
        String key = "S5-F8::" + paymentId;
        PaymentDetailsDTO cached = cache.get(key, PaymentDetailsDTO.class);
        if (cached != null) return cached;

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found"));

        List<PaymentCoupon> paymentCoupons = paymentCouponRepository.findByPaymentId(paymentId);
        List<PaymentDetailsDTO.AppliedCouponDTO> appliedCouponDTOs = paymentCoupons.stream()
                .map(pc -> new PaymentDetailsDTO.AppliedCouponDTO(
                        pc.getCoupon().getCode(),
                        pc.getCoupon().getDiscountType().name(),
                        pc.getDiscountApplied(),
                        pc.getAppliedAt()))
                .collect(Collectors.toList());

        Double totalDiscount = appliedCouponDTOs.stream()
                .mapToDouble(PaymentDetailsDTO.AppliedCouponDTO::getDiscountApplied).sum();
        Double finalAmount = payment.getAmount() - totalDiscount;
        Map<String, Object> transactionDetails = payment.getTransactionDetails() != null
                ? payment.getTransactionDetails() : new HashMap<>();

        PaymentDetailsDTO result = new PaymentDetailsDTO(
                payment.getId(), payment.getRideId(), payment.getUserId(),
                payment.getAmount(), payment.getMethod(), payment.getStatus(),
                transactionDetails, appliedCouponDTOs, totalDiscount, finalAmount);
        cache.put(key, result, TTL_DETAIL);
        return result;
    }

    // §4.4.1 — S5-F9: top used coupons, cached 10 min
    public List<CouponUsageDTO> getTopUsedCoupons(int limit) {
        String key = "S5-F9::" + limit;
        List<CouponUsageDTO> cached = cache.get(key, new TypeReference<List<CouponUsageDTO>>() {});
        if (cached != null) return cached;

        List<Object[]> rows = couponRepository.findTopUsedCoupons(limit);
        List<CouponUsageDTO> result = rows.stream().map(row -> {
            Long couponId = ((Number) row[0]).longValue();
            String code = (String) row[1];
            String discountType = (String) row[2];
            Double discountValue = ((Number) row[3]).doubleValue();
            Integer timesUsed = ((Number) row[4]).intValue();
            Double totalDiscountGiven = ((Number) row[5]).doubleValue();
            Boolean active = (Boolean) row[6];
            LocalDateTime expiryDate;
            if (row[7] instanceof java.sql.Timestamp)
                expiryDate = ((java.sql.Timestamp) row[7]).toLocalDateTime();
            else if (row[7] instanceof LocalDateTime)
                expiryDate = (LocalDateTime) row[7];
            else
                expiryDate = LocalDateTime.parse(row[7].toString());
            Boolean expired = expiryDate.isBefore(LocalDateTime.now());
            return new CouponUsageDTO(couponId, code, discountType, discountValue,
                    timesUsed, totalDiscountGiven, active, expired);
        }).collect(Collectors.toList());

        cache.put(key, result, TTL_ANALYTICS);
        return result;
    }

    // ── M2 Features ──────────────────────────────────────────────────────────

    // §10.5.1
    public List<VehicleTypeRevenueDTO> getFareRevenueByVehicleType(String startDateStr, String endDateStr) {
        LocalDateTime startDate = parseDate(startDateStr, false);
        LocalDateTime endDate = parseDate(endDateStr, true);

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }

        // §10.5.1(f)
        try {
            PaymentAuditEvent event = new PaymentAuditEvent(
                    null, // Analytics are not tied to a specific payment ID
                    "ANALYTICS_VIEWED",
                    LocalDateTime.now(),
                    null,
                    null,
                    Map.of("dashboard", "vehicle-type-revenue", "startDate", startDate.toString(), "endDate", endDate.toString())
            );
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("MongoDB audit event write failed: {}", e.getMessage());
        }

        // (10 min TTL)
        String key = "S5-F10::" + startDate + "::" + endDate;
        List<VehicleTypeRevenueDTO> cached = cache.get(key, new TypeReference<List<VehicleTypeRevenueDTO>>() {});
        if (cached != null) return cached;

        List<Object[]> rows = paymentRepository.findVehicleTypeRevenueStats(startDate, endDate);
        List<VehicleTypeRevenueDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            String vehicleType = (String) row[0];
            Double surgeFeeRevenue = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            Double baseFareRevenue = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            Double totalRevenue = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            Long rideCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;

            result.add(VehicleTypeRevenueDTO.builder()
                    .vehicleType(vehicleType)
                    .surgeFeeRevenue(surgeFeeRevenue)
                    .baseFareRevenue(baseFareRevenue)
                    .totalRevenue(totalRevenue)
                    .rideCount(rideCount)
                    .build());
        }

        cache.put(key, result, TTL_ANALYTICS);
        return result;
    }

    // §10.5.3 — S5-F12: surge-adjusted refund with Strategy pattern
    @Transactional
    public Payment refundSurgeAdjusted(Long id, RefundRequest request) {
        Payment payment = getPaymentById(id);

        if (payment.getStatus() != PaymentStatus.COMPLETED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment must be COMPLETED to be refunded");

        // §3.2 — No if/else — selector chooses strategy
        RefundStrategySelector selector = new RefundStrategySelector();
        RefundStrategy strategy = selector.select(payment, request);
        RefundResult result = strategy.calculateRefund(payment, request);

        // §10.5.3e — NoRefundStrategy path
        if (result.getAmount() == 0.0 && result.getReasonCode().equals("refund window expired")) {
            saveAuditEvent(payment, "REFUND_DENIED", Map.of(
                    "strategyName", result.getStrategyName(),
                    "reason", result.getReasonCode()));
            evictPaymentCaches(id);
            evictAnalyticsCaches();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refund window expired");
        }

        // §10.5.3f,g — Update payment
        payment.setStatus(PaymentStatus.REFUNDED);
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundAmount", result.getAmount());
        details.put("refundSurgeIncluded", request.isRefundSurge());
        details.put("refundReason", request.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);
        Payment saved = paymentRepository.save(payment);

        // §10.5.3h — Log REFUNDED to MongoDB
        saveAuditEvent(saved, "REFUNDED", Map.of(
                "strategyName", result.getStrategyName(),
                "reason", request.getReason(),
                "originalAmount", payment.getAmount(),
                "refundAmount", result.getAmount(),
                "surgeIncluded", request.isRefundSurge()));

        // §10.5.3i — Invalidate caches
        evictPaymentCaches(id);
        evictAnalyticsCaches();
        return saved;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public LocalDateTime parseDate(String date, boolean endOfDay) {
        if (date == null) return null;
        if (date.contains("T")) return LocalDateTime.parse(date);
        return endOfDay
                ? LocalDateTime.parse(date + "T23:59:59.999999999")
                : LocalDateTime.parse(date + "T00:00:00");
    }

    // §7.1.6 — Save PaymentAuditEvent to MongoDB (soft dependency)
    private void saveAuditEvent(Payment payment, String action, Map<String, Object> details) {
        try {
            PaymentAuditEvent event = new PaymentAuditEvent(
                    payment.getId(),
                    action,
                    LocalDateTime.now(),
                    payment.getMethod() != null ? payment.getMethod().name() : null,
                    payment.getAmount(),
                    details);
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("MongoDB audit event write failed: {}", e.getMessage());
        }
    }
}