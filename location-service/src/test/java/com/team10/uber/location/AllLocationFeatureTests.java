package com.team10.uber.location;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        S4F1LatestLocationTests.class,
        S4F2UpdateLocationTests.class,
        S4F3NearbyDriversTests.class,
        S4F4BatchLocationTests.class,
        S4F5MetadataSearchTests.class,
        S4F6HistoryTests.class,
        S4F7PurgeLocationTests.class,
        S4F8DriverSummaryTests.class,
        S4F9StationaryTests.class,
        S4F10AnalyticsTests.class,
        S4F11TrackingTests.class,
        S4F12TimelineTests.class
})
public class AllLocationFeatureTests {
}
// to run all
// .\mvnw -pl location-service test "-Dtest=S4F*Tests"