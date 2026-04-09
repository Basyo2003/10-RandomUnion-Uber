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
import org.springframework.transaction.annotation.Transactional;
import com.team10.uber.payment.dto.UserPaymentSummaryDTO;
import com.team10.uber.payment.dto.ProcessPaymentDTO;
import com.team10.uber.payment.enums.PaymentMethod;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import com.team10.uber.payment.enums.PaymentStatus;
import java.util.Map;
import java.util.HashMap;

import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
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
}