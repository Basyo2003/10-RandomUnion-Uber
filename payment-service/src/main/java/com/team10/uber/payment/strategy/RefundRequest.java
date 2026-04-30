package com.team10.uber.payment.strategy;

public class RefundRequest {
    private String reason;
    private boolean refundSurge;

    public RefundRequest() {}

    public RefundRequest(String reason, boolean refundSurge) {
        this.reason = reason;
        this.refundSurge = refundSurge;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isRefundSurge() { return refundSurge; }
    public void setRefundSurge(boolean refundSurge) { this.refundSurge = refundSurge; }
}