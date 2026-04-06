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


import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment createPayment(Payment payment) {
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
}