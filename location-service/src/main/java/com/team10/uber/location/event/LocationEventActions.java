package com.team10.uber.location.event;

public final class LocationEventActions {

    private LocationEventActions() {
    }

    // Main required actions from 7.1.5
    public static final String TRACKING_RECORDED = "TRACKING_RECORDED";
    public static final String ANALYTICS_VIEWED = "ANALYTICS_VIEWED";

    // M1 / Location Service retrofits
    public static final String LOCATION_UPDATED = "LOCATION_UPDATED"; // S4-F2
    public static final String BATCH_LOCATIONS_UPDATED = "BATCH_LOCATIONS_UPDATED"; // S4-F4
    public static final String OLD_LOCATIONS_PURGED = "OLD_LOCATIONS_PURGED"; // S4-F7
    public static final String LOCATION_DELETED = "LOCATION_DELETED"; // CRUD delete

    // Optional but useful if you log read/query endpoints
    public static final String LATEST_LOCATION_VIEWED = "LATEST_LOCATION_VIEWED"; // S4-F1
    public static final String NEARBY_DRIVERS_VIEWED = "NEARBY_DRIVERS_VIEWED"; // S4-F3
    public static final String STATIONARY_DRIVERS_VIEWED = "STATIONARY_DRIVERS_VIEWED"; // S4-F9
    //M3
    public static final String TRIP_COMPLETED = "TRIP_COMPLETED";
    public static final String TRIP_CANCELLED = "TRIP_CANCELLED";
}