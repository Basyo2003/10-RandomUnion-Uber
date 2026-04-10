package com.team10.uber.payment.dto;

public class ProcessPaymentDTO {
    private String method;
    private String cardLastFour;

    // Getters and Setters
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getCardLastFour() { return cardLastFour; }
    public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
}