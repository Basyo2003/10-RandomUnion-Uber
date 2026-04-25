package com.team10.uber.ride.factory;

import com.team10.uber.ride.event.*;

import java.time.LocalDateTime;
import java.util.Map;

public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        LocalDateTime timestamp = LocalDateTime.now();
        return switch (type) {
            case AUTH -> {
                AuthEvent event = new AuthEvent();
                event.setUserId(extractLong(params, "userId"));
                event.setAction(extractString(params, "action"));
                event.setTimestamp(timestamp);
                event.setDetails(params);
                yield event;
            }
            case DRIVER -> {
                DriverEvent event = new DriverEvent();
                event.setDriverId(extractLong(params, "driverId"));
                event.setAction(extractString(params, "action"));
                event.setTimestamp(timestamp);
                event.setDetails(params);
                yield event;
            }
            case RIDE -> {
                RideEvent event = new RideEvent();
                event.setRideId(extractLong(params, "rideId"));
                event.setAction(extractString(params, "action"));
                event.setTimestamp(timestamp);
                event.setDetails(params);
                yield event;
            }
            case LOCATION -> {
                LocationEvent event = new LocationEvent();
                event.setDriverId(extractLong(params, "driverId"));
                event.setAction(extractString(params, "action"));
                event.setTimestamp(timestamp);
                event.setDetails(params);
                yield event;
            }
            case PAYMENT_AUDIT -> {
                PaymentAuditEvent event = new PaymentAuditEvent();
                event.setPaymentId(extractLong(params, "paymentId"));
                event.setAction(extractString(params, "action"));
                event.setTimestamp(timestamp);
                event.setMethod(extractString(params, "method"));
                event.setAmount(extractDouble(params, "amount"));
                event.setDetails(params);
                yield event;
            }
        };
    }

    private Long extractLong(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return i.longValue();
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private String extractString(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    private Double extractDouble(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) return null;
        if (val instanceof Double d) return d;
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(val.toString());
    }
}