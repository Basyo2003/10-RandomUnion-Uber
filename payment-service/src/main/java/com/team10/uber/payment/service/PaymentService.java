package com.team10.uber.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team10.uber.contracts.dto.DriverDTO;
import com.team10.uber.contracts.dto.RideDTO;
import com.team10.uber.contracts.events.PaymentCompletedEvent;
import com.team10.uber.contracts.events.PaymentFailedEvent;
import com.team10.uber.contracts.feign.DriverServiceClient;
import com.team10.uber.contracts.feign.RideServiceClient;
import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.payment.adapter.MongoDocumentAdapter;
import com.team10.uber.payment.cache.RedisCacheService;
import com.team10.uber.payment.config.PaymentRabbitConfig;
import com.team10.uber.payment.dto.CouponUsageDTO;
import com.team10.uber.payment.dto.PaymentAuditEventDTO;
import com.team10.uber.payment.dto.PaymentDetailsDTO;
import com.team10.uber.payment.dto.PaymentMethodDTO;
import com.team10.uber.payment.dto.ProcessPaymentDTO;
import com.team10.uber.payment.dto.RevenueReportDTO;
import com.team10.uber.payment.dto.UserPaymentSummaryDTO;
import com.team10.uber.payment.dto.VehicleTypeRevenueDTO;
import com.team10.uber.payment.enums.DiscountType;
import com.team10.uber.payment.enums.PaymentMethod;
import com.team10.uber.payment.enums.PaymentStatus;
import com.team10.uber.payment.model.Coupon;
import com.team10.uber.payment.model.Payment;
import com.team10.uber.payment.model.PaymentCoupon;
import com.team10.uber.payment.observer.AbstractObservableService;
import com.team10.uber.payment.observer.MongoEventLogger;
import com.team10.uber.payment.repository.CouponRepository;
import com.team10.uber.payment.repository.PaymentCouponRepository;
import com.team10.uber.payment.repository.PaymentRepository;
import com.team10.uber.payment.strategy.RefundRequest;
import com.team10.uber.payment.strategy.RefundResult;
import com.team10.uber.payment.strategy.RefundStrategy;
import com.team10.uber.payment.strategy.RefundStrategySelector;

import feign.FeignException;
import jakarta.annotation.PostConstruct;

@Service
public class PaymentService extends AbstractObservableService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // §8.1 TTL constants
    private static final long TTL_ANALYTICS = 10 * 60L; // 10 min — dashboards/analytics
    private static final long TTL_SEARCH = 5 * 60L; // 5 min — search results
    private static final long TTL_DETAIL = 15 * 60L; // 15 min — entity detail views

    private final PaymentRepository paymentRepository;
    private final CouponRepository couponRepository;
    private final PaymentCouponRepository paymentCouponRepository;
    private final MongoEventLogger mongoEventLogger;
    private final RedisCacheService cache;
    private final MongoTemplate mongoTemplate;
    private final UserServiceClient userServiceClient;
    private final RideServiceClient rideServiceClient;
    private final DriverServiceClient driverServiceClient;
    private final RabbitTemplate rabbitTemplate;
    // §3.8 — Adapter instances
    private final com.team10.uber.payment.adapter.PaymentSummaryAdapter paymentSummaryAdapter = new com.team10.uber.payment.adapter.PaymentSummaryAdapter();
    private final com.team10.uber.payment.adapter.RevenueReportAdapter revenueReportAdapter = new com.team10.uber.payment.adapter.RevenueReportAdapter();
    private final com.team10.uber.payment.adapter.CouponUsageAdapter couponUsageAdapter = new com.team10.uber.payment.adapter.CouponUsageAdapter();

    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepository,
            CouponRepository couponRepository,
            PaymentCouponRepository paymentCouponRepository,
            MongoEventLogger mongoEventLogger,
            RedisCacheService cache,
            MongoTemplate mongoTemplate, org.springframework.context.ApplicationEventPublisher eventPublisher,
            UserServiceClient userServiceClient,
            RideServiceClient rideServiceClient,
            DriverServiceClient driverServiceClient,
            RabbitTemplate rabbitTemplate) {
        this.paymentRepository = paymentRepository;
        this.couponRepository = couponRepository;
        this.paymentCouponRepository = paymentCouponRepository;
        this.mongoEventLogger = mongoEventLogger;
        this.cache = cache;
        this.mongoTemplate = mongoTemplate;
        this.userServiceClient = userServiceClient;
        this.rideServiceClient = rideServiceClient;
        this.driverServiceClient = driverServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.eventPublisher = eventPublisher;
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
        try {
            userServiceClient.getUser(payment.getUserId());
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User not found with id: " + payment.getUserId());
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User not found with id: " + payment.getUserId());
        }
        try {
            rideServiceClient.getRide(payment.getRideId());
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ride not found with id: " + payment.getRideId());
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ride not found with id: " + payment.getRideId());
        }
        if (paymentRepository.countPaymentsByRideId(payment.getRideId()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A payment already exists for ride id: " + payment.getRideId());

        Payment saved = paymentRepository.save(payment);
        notifyObservers("CREATED", saved);

        // saveAuditEvent(saved, "CREATED", Map.of(
        // "rideId", saved.getRideId(),
        // "userId", saved.getUserId()));

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
        if (cached != null)
            return cached;

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

        // saveAuditEvent(payment, "PAYMENT_DELETED", Map.of(
        // "paymentId", id));

        evictPaymentCaches(id);
        evictAnalyticsCaches();
    }

    // ── M1 Features ──────────────────────────────────────────────────────────

    // §4.4.1 — S5-F1: search payments, cached 5 min
    public List<Payment> searchPayments(String status, LocalDateTime startDate, LocalDateTime endDate, Long userId) {
        String key = "S5-F1::" + status + "::" + startDate + "::" + endDate + "::" + userId;
        List<Payment> cached = cache.get(key, new TypeReference<List<Payment>>() {
        });
        if (cached != null)
            return cached;

        List<Payment> result = paymentRepository.searchPayments(
                (status == null || status.isEmpty()) ? null : status, startDate, endDate, userId);
        cache.put(key, result, TTL_SEARCH);
        return result;
    }

    // §4.4.1 — S5-F2: refund payment — write, invalidates caches
    @Transactional
    public Payment refundPayment(Long id, String reason) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
        String currentStatus = paymentRepository.findStatusAsTextById(id);
        if (!"COMPLETED".equals(currentStatus))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment cannot be refunded. Current status: " + currentStatus);

        payment.setStatus(PaymentStatus.REFUNDED);
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null)
            details = new HashMap<>();
        details.put("refundReason", reason);
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);

        Payment saved = paymentRepository.save(payment);
        notifyObservers("REFUNDED", saved);

        // saveAuditEvent(saved, "REFUNDED", Map.of(
        // "refundReason", reason,
        // "refundedAt", LocalDateTime.now().toString()));

        evictPaymentCaches(id);
        evictAnalyticsCaches();
        return saved;
    }

    // §7 S5-F3: Feign replaces cross-service SQL (countUserById removed)
    public UserPaymentSummaryDTO getUserPaymentSummary(Long userId) {
        log.info("getUserPaymentSummary: userId={}", userId);
        MDC.put("userId", String.valueOf(userId));
        String key = "S5-F3::" + userId;
        UserPaymentSummaryDTO cached = cache.get(key, UserPaymentSummaryDTO.class);
        if (cached != null) {
            MDC.remove("userId");
            return cached;
        }

        try {
            userServiceClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            MDC.remove("userId");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
        } catch (FeignException e) {
            log.warn("user-service unreachable for userId={}: {}", userId, e.getMessage());
            MDC.remove("userId");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user-service unavailable");
        }

        List<Object[]> results = paymentRepository.findPaymentSummaryByUserId(userId);
        UserPaymentSummaryDTO result = paymentSummaryAdapter.adapt(userId, results);
        cache.put(key, result, TTL_SEARCH);
        MDC.remove("userId");
        return result;
    }

    // §7 S5-F4: Feign replaces cross-service SQL; validates PAYMENT_PENDING;
    // publishes events
    @Transactional
    public Payment processPaymentForRide(Long rideId, ProcessPaymentDTO dto, boolean simulateFailure) {
        log.info("processPaymentForRide: rideId={}", rideId);
        MDC.put("rideId", String.valueOf(rideId));

        // a) Fetch ride via Feign — 404 if not found
        RideDTO ride;
        try {
            ride = rideServiceClient.getRide(rideId);
        } catch (FeignException.NotFound e) {
            MDC.remove("rideId");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found with id: " + rideId);
        } catch (FeignException e) {
            log.warn("ride-service unreachable for rideId={}: {}", rideId, e.getMessage());
            MDC.remove("rideId");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ride-service unavailable");
        }

        // b) Validate ride status == PAYMENT_PENDING
        if (!"PAYMENT_PENDING".equals(ride.status())) {
            MDC.remove("rideId");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride is not in PAYMENT_PENDING status. Current status: " + ride.status());
        }

        // c) Require pre-existing PENDING payment (created by ride.completed event consumer in S5-EVENTS)
        Payment payment = paymentRepository.findByRideIdAndStatus(rideId, PaymentStatus.PENDING)
                .orElseThrow(() -> {
                    MDC.remove("rideId");
                    return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No PENDING payment found for ride id: " + rideId);
                });

        MDC.put("paymentId", String.valueOf(payment.getId()));
        MDC.put("userId", String.valueOf(payment.getUserId()));

        // d) Method validation
        if (dto.getMethod().equals("CREDIT_CARD") && dto.getCardLastFour() != null) {
            String card = dto.getCardLastFour();
            if (card.length() != 4 || !card.matches("\\d{4}")) {
                MDC.remove("rideId"); MDC.remove("paymentId"); MDC.remove("userId");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cardLastFour must be exactly 4 digits");
            }
        }

        // d-ii) Unsupported method is a payment failure — publish payment.failed + return 400
        try {
            payment.setMethod(PaymentMethod.valueOf(dto.getMethod()));
        } catch (IllegalArgumentException e) {
            payment.setStatus(PaymentStatus.FAILED);
            Map<String, Object> failDetails = new HashMap<>();
            failDetails.put("gatewayResponse", "declined");
            failDetails.put("failureReason", "unsupported payment method: " + dto.getMethod());
            payment.setTransactionDetails(failDetails);
            Payment failSaved = paymentRepository.save(payment);
            rabbitTemplate.convertAndSend(PaymentRabbitConfig.PAYMENT_EXCHANGE,
                    PaymentRabbitConfig.ROUTING_KEY_FAILED,
                    new com.team10.uber.contracts.events.PaymentFailedEvent(
                            failSaved.getId(), rideId, "unsupported payment method: " + dto.getMethod()));
            log.info("payment.failed published (unsupported method): paymentId={}, rideId={}", failSaved.getId(), rideId);
            MDC.remove("rideId"); MDC.remove("paymentId"); MDC.remove("userId");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported payment method: " + dto.getMethod());
        }

        // e) Compute surgeFee from ride metadata (15% fallback)
        double amount = payment.getAmount() != null ? payment.getAmount()
                : (ride.fare() != null ? ride.fare() : 0.0);
        Object surgeMult = ride.metadata() != null ? ride.metadata().get("surgeMultiplier") : null;
        double surgeFee = surgeMult != null
                ? amount * (Double.parseDouble(surgeMult.toString()) - 1.0)
                : amount * 0.15;


        Map<String, Object> transactionDetails = new HashMap<>();
        transactionDetails.put("gatewayResponse", "success");
        transactionDetails.put("cardLastFour", dto.getCardLastFour());
        transactionDetails.put("receiptUrl", "https://receipts.com/" + rideId);
        transactionDetails.put("failureReason", null);
        transactionDetails.put("surgeFee", surgeFee);
        payment.setTransactionDetails(transactionDetails);

        // f) simulateFailure → FAILED + publish payment.failed
        if (simulateFailure) {
            payment.setStatus(PaymentStatus.FAILED);
            transactionDetails.put("gatewayResponse", "declined");
            transactionDetails.put("failureReason", "simulated failure");
            payment.setTransactionDetails(transactionDetails);
            Payment saved = paymentRepository.save(payment);
            notifyObservers("FAILED", saved);
            rabbitTemplate.convertAndSend(PaymentRabbitConfig.PAYMENT_EXCHANGE,
                    PaymentRabbitConfig.ROUTING_KEY_FAILED,
                    Map.of("paymentId", saved.getId(), "rideId", rideId, "userId", saved.getUserId()));
            log.info("payment.failed published: paymentId={}, rideId={}", saved.getId(), rideId);
            evictPaymentCaches(saved.getId());
            evictAnalyticsCaches();
            MDC.remove("rideId"); MDC.remove("paymentId"); MDC.remove("userId");
            return saved;
        }

        // g) Normal path — COMPLETED + publish payment.completed
        payment.setStatus(PaymentStatus.COMPLETED);
        Payment saved = paymentRepository.save(payment);
        notifyObservers("COMPLETED", saved);
        rabbitTemplate.convertAndSend(PaymentRabbitConfig.PAYMENT_EXCHANGE,
                PaymentRabbitConfig.ROUTING_KEY_COMPLETED,
                Map.of("paymentId", saved.getId(), "rideId", rideId, "userId", saved.getUserId()));
        log.info("payment.completed published: paymentId={}, rideId={}", saved.getId(), rideId);

        cache.evictByPattern("S5-F1::*");
        cache.evictByPattern("S5-F3::" + saved.getUserId());
        evictAnalyticsCaches();
        MDC.remove("rideId"); MDC.remove("paymentId"); MDC.remove("userId");
        return saved;
    }

    // §4.4.1 — S5-F5: apply coupon — write, invalidates caches
    @Transactional
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
        if (discount > payment.getAmount())
            discount = payment.getAmount();

        PaymentCoupon paymentCoupon = new PaymentCoupon();
        paymentCoupon.setPayment(payment);
        paymentCoupon.setCoupon(coupon);
        paymentCoupon.setDiscountApplied(discount);
        coupon.setCurrentUses(coupon.getCurrentUses() + 1);

        paymentCouponRepository.save(paymentCoupon);
        couponRepository.save(coupon);
        notifyObservers("COUPON_APPLIED", payment);

        // saveAuditEvent(payment, "COUPON_APPLIED", Map.of(
        //         "couponId", couponId,
        //         "discountApplied", discount));

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

        // §3.8 — Use Adapter instead of inline Object[] mapping
        RevenueReportDTO result = revenueReportAdapter.adapt(revenueRow, refundRow);
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
        if (details == null)
            details = new HashMap<>();
        int retryAttempt = details.containsKey("retryAttempt")
                ? ((Number) details.get("retryAttempt")).intValue() + 1
                : 1;
        details.put("retryAttempt", retryAttempt);
        details.put("gatewayResponse", "approved");
        payment.setTransactionDetails(details);

        Payment saved = paymentRepository.save(payment);
        notifyObservers("RETRY_ATTEMPTED", saved);

        // saveAuditEvent(saved, "RETRY_ATTEMPTED", Map.of(
        //         "retryAttempt", retryAttempt,
        //         "gatewayResponse", "approved"));

        evictPaymentCaches(id);
        evictAnalyticsCaches();
        return saved;
    }

    // §4.4.1 — S5-F8: payment details, cached 15 min
    public PaymentDetailsDTO getPaymentDetails(Long paymentId) {
        String key = "S5-F8::" + paymentId;
        PaymentDetailsDTO cached = cache.get(key, PaymentDetailsDTO.class);
        if (cached != null)
            return cached;

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
                ? payment.getTransactionDetails()
                : new HashMap<>();

        PaymentDetailsDTO result = new PaymentDetailsDTO(
                payment.getId(), payment.getRideId(), payment.getUserId(),
                payment.getAmount(), payment.getMethod(), payment.getStatus(),
                transactionDetails, appliedCouponDTOs, totalDiscount, finalAmount);
        cache.put(key, result, TTL_DETAIL);
        return result;
    }

    public List<CouponUsageDTO> getTopUsedCoupons(int limit) {
        String key = "S5-F9::" + limit;
        List<CouponUsageDTO> cached = cache.get(key,
                new TypeReference<List<CouponUsageDTO>>() {});
        if (cached != null) return cached;

        List<Object[]> rows = couponRepository.findTopUsedCoupons(limit);

        // §3.8 — Use Adapter instead of inline Object[] mapping
        List<CouponUsageDTO> result = couponUsageAdapter.adaptAll(rows);
        cache.put(key, result, TTL_ANALYTICS);
        return result;
    }

    // ── M2 Features ──────────────────────────────────────────────────────────

    // §10.5.1 S5-F10: local query (cap 100) + per-ride Feign + driverId→vehicleType
    // cache (§2.12)
    public List<VehicleTypeRevenueDTO> getFareRevenueByVehicleType(String startDateStr, String endDateStr) {
        long startTime = System.currentTimeMillis();
        LocalDateTime startDate = parseDate(startDateStr, false);
        LocalDateTime endDate = parseDate(endDateStr, true);

        if (startDate.isAfter(endDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");

        notifyObservers("ANALYTICS_VIEWED", Map.of(
                "dashboard", "vehicle-type-revenue",
                "startDate", startDate.toString(),
                "endDate", endDate.toString()));

        String key = "S5-F10::" + startDate + "::" + endDate;
        List<VehicleTypeRevenueDTO> cached = cache.get(key, new TypeReference<List<VehicleTypeRevenueDTO>>() {});
        if (cached != null) return cached;

        // §2.12: local COMPLETED payments, capped at 100 distinct rideIds
        List<Payment> payments = paymentRepository.findCompletedPaymentsInDateRangeCapped(startDate, endDate);

        // §2.12: per-request driverId → vehicleType cache to avoid duplicate Feign calls
        Map<Long, String> vehicleTypeByDriver = new HashMap<>();
        Map<String, List<Payment>> byVehicleType = new HashMap<>();

        for (Payment payment : payments) {
            RideDTO ride;
            try {
                ride = rideServiceClient.getRide(payment.getRideId());
            } catch (FeignException e) {
                log.warn("ride-service call skipped for rideId={}: {}", payment.getRideId(), e.getMessage());
                continue;
            }

            Long driverId = ride.driverId();
            if (driverId == null) continue;

            String vehicleType = vehicleTypeByDriver.computeIfAbsent(driverId, id -> {
                try {
                    DriverDTO driver = driverServiceClient.getDriver(id);
                    Object vt = driver.vehicleDetails() != null
                            ? driver.vehicleDetails().get("vehicleType") : null;
                    return vt != null ? vt.toString() : "UNKNOWN";
                } catch (FeignException e) {
                    log.warn("driver-service call failed for driverId={}: {}", id, e.getMessage());
                    return "UNKNOWN";
                }
            });

            byVehicleType.computeIfAbsent(vehicleType, k -> new ArrayList<>()).add(payment);
        }

        List<VehicleTypeRevenueDTO> result = byVehicleType.entrySet().stream().map(entry -> {
            List<Payment> group = entry.getValue();
            double total = group.stream().mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0).sum();
            double surgeFee = group.stream().mapToDouble(p -> {
                Object sf = p.getTransactionDetails() != null
                        ? p.getTransactionDetails().get("surgeFee") : null;
                return sf != null ? ((Number) sf).doubleValue() : p.getAmount() * 0.15;
            }).sum();
            return VehicleTypeRevenueDTO.builder()
                    .vehicleType(entry.getKey())
                    .surgeFeeRevenue(surgeFee)
                    .baseFareRevenue(total - surgeFee)
                    .totalRevenue(total)
                    .rideCount((long) group.size())
                    .build();
        }).collect(Collectors.toList());

        cache.put(key, result, TTL_ANALYTICS);

        // §11.1: slow operation WARN
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > 1000)
            log.warn("Slow getFareRevenueByVehicleType took {}ms", elapsed);

        return result;
    }

    // §10.5.2 — S5-F11: payment method breakdown from MongoDB via Adapter pattern
    public List<PaymentMethodDTO> getPaymentMethodBreakdown(String startDateStr, String endDateStr) {
        LocalDateTime startDate = parseDate(startDateStr, false);
        LocalDateTime endDate = parseDate(endDateStr, true);

        if (startDate.isAfter(endDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        // Log ANALYTICS_VIEWED (pure observability - same pattern as S5-F10)
        notifyObservers("ANALYTICS_VIEWED", Map.of(
                "dashboard", "payment-method-breakdown",
                "startDate", startDate.toString(),
                "endDate", endDate.toString()));

        String key = "S5-F11::" + startDate + "::" + endDate;
        List<PaymentMethodDTO> cached = cache.get(key, new TypeReference<List<PaymentMethodDTO>>() {});
        if (cached != null) return cached;

        // §3.8 — Adapter pattern: query raw Documents, then adapt to DTO
        java.util.Date start = java.util.Date.from(startDate.atZone(java.time.ZoneId.systemDefault()).toInstant());
        java.util.Date end   = java.util.Date.from(endDate.atZone(java.time.ZoneId.systemDefault()).toInstant());
        Query query = new Query(
                Criteria.where("action").in("COMPLETED", "FAILED")
                        .and("timestamp").gte(start).lte(end));

        MongoDocumentAdapter adapter = new MongoDocumentAdapter();
        List<Document> docs = mongoTemplate.find(query, Document.class, "payment_audit_trail");
        List<PaymentAuditEventDTO> events = docs.stream()
                .map(adapter::adapt)
                .collect(Collectors.toList());

        Map<String, List<PaymentAuditEventDTO>> byMethod = events.stream()
                .filter(e -> e.getMethod() != null)
                .collect(Collectors.groupingBy(PaymentAuditEventDTO::getMethod));

        List<PaymentMethodDTO> result = byMethod.entrySet().stream().map(entry -> {
            List<PaymentAuditEventDTO> group = entry.getValue();
            long successCount = group.stream().filter(e -> "COMPLETED".equals(e.getAction())).count();
            long failureCount = group.stream().filter(e -> "FAILED".equals(e.getAction())).count();
            double totalAmount = group.stream()
                    .filter(e -> "COMPLETED".equals(e.getAction()) && e.getAmount() != null)
                    .mapToDouble(PaymentAuditEventDTO::getAmount).sum();
            double successRate = (successCount + failureCount) > 0
                    ? (double) successCount / (successCount + failureCount) : 0.0;
            return PaymentMethodDTO.builder()
                    .method(entry.getKey())
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .successRate(successRate)
                    .totalAmount(totalAmount)
                    .build();
        }).collect(Collectors.toList());

        cache.put(key, result, TTL_ANALYTICS);
        return result;
    }

    // Tiny helper class for grouping (private inside service)
    private static class PaymentMethodStats {
        long successCount = 0;
        long failureCount = 0;
        double totalAmount = 0.0;
    }

    // §10.5.3 — S5-F12: surge-adjusted refund with Strategy pattern
    @Transactional
    public Payment refundSurgeAdjusted(Long id, RefundRequest request) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
        String currentStatus = paymentRepository.findStatusAsTextById(id);
        if (!"COMPLETED".equals(currentStatus))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment must be COMPLETED to be refunded");

        // §3.2 — No if/else — selector chooses strategy
        RefundStrategySelector selector = new RefundStrategySelector();
        RefundStrategy strategy = selector.select(payment, request);
        RefundResult result = strategy.calculateRefund(payment, request);

        // §10.5.3e — NoRefundStrategy path
        if (result.getAmount() == 0.0 && result.getReasonCode().equals("refund window expired")) {
            notifyObservers("REFUND_DENIED", payment);
            evictPaymentCaches(id);
            evictAnalyticsCaches();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refund window expired");
        }

        // §10.5.3f,g — Update payment
        payment.setStatus(PaymentStatus.REFUNDED);
        Map<String, Object> details = payment.getTransactionDetails();
        if (details == null)
            details = new HashMap<>();
        details.put("refundAmount", result.getAmount());
        details.put("refundSurgeIncluded", request.isRefundSurge());
        details.put("refundReason", request.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());
        payment.setTransactionDetails(details);
        Payment saved = paymentRepository.save(payment);

        // §10.5.3h — Log REFUNDED with strategyName to MongoDB via observer
        Map<String, Object> refundAudit = new HashMap<>();
        refundAudit.put("paymentId", saved.getId());
        refundAudit.put("method", saved.getMethod() != null ? saved.getMethod().name() : null);
        refundAudit.put("amount", saved.getAmount());
        refundAudit.put("strategyName", result.getStrategyName());
        refundAudit.put("reason", request.getReason());
        refundAudit.put("originalAmount", payment.getAmount());
        refundAudit.put("refundAmount", result.getAmount());
        refundAudit.put("surgeIncluded", request.isRefundSurge());
        notifyObservers("REFUNDED", refundAudit);

        // §10.5.3i — Invalidate caches
        evictPaymentCaches(id);
        evictAnalyticsCaches();
        return saved;
    }

    // S5-READ-DB: total completed payments for a user in a date range (called by
    // user-service via Feign)
    public BigDecimal getUserPaymentTotal(Long userId, String startDate, String endDate) {
        log.info("getUserPaymentTotal: userId={}, startDate={}, endDate={}", userId, startDate, endDate);
        MDC.put("userId", String.valueOf(userId));
        LocalDateTime start = parseDate(startDate, false);
        LocalDateTime end   = parseDate(endDate, true);
        BigDecimal total = paymentRepository.sumCompletedAmountByUserIdAndDateRange(userId, start, end);
        BigDecimal result = total != null ? total : BigDecimal.ZERO;
        log.info("getUserPaymentTotal: userId={} → {}", userId, result);
        MDC.remove("userId");
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public LocalDateTime parseDate(String date, boolean endOfDay) {
        if (date == null)
            return null;
        if (date.contains("T"))
            return LocalDateTime.parse(date);
        return endOfDay
                ? LocalDateTime.parse(date + "T23:59:59.999999999")
                : LocalDateTime.parse(date + "T00:00:00");
    }

    // §7.1.6 — Save PaymentAuditEvent to MongoDB via EventFactory (Factory pattern
    // — no direct new)
    private void saveAuditEvent(Payment payment, String action, Map<String, Object> details) {
        try {
            boolean requiresMethodAndAmount = List.of(
                    "CREATED", "COMPLETED", "FAILED",
                    "REFUNDED", "REFUND_DENIED",
                    "COUPON_APPLIED", "RETRY_ATTEMPTED"
            ).contains(action);

            Map<String, Object> params = new HashMap<>(details);
            params.put("paymentId", payment.getId());
            params.put("action", action);
            params.put("method", requiresMethodAndAmount && payment.getMethod() != null
                    ? payment.getMethod().name() : null);
            params.put("amount", requiresMethodAndAmount ? payment.getAmount() : null);

            com.team10.uber.payment.event.MongoEvent event =
                    new com.team10.uber.payment.factory.EventFactory()
                            .createEvent(com.team10.uber.payment.factory.EventType.PAYMENT_AUDIT, params);
            mongoEventLogger.onEvent(action, params);
        } catch (Exception e) {
            log.warn("MongoDB audit event write failed: {}", e.getMessage());
        }
    }
    // §7.1.6 — Save PaymentAuditEvent to MongoDB via Observer pattern (EventFactory
    // handles creation)

    private void logPaymentEvent(Payment payment, String action, Map<String, Object> details) {
        Map<String, Object> merged = new HashMap<>(details);
        merged.put("action", action);
        notifyObservers(action, payment);
    }

}