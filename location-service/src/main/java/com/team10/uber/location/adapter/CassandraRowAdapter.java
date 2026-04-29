package com.team10.uber.location.adapter;

import com.datastax.oss.driver.api.core.cql.Row;
import com.team10.uber.location.dto.LocationTrackingDTO;

public class CassandraRowAdapter implements NoSqlAdapter<Row, LocationTrackingDTO> {
    @Override
    public LocationTrackingDTO adapt(Row source) {
        LocationTrackingDTO dto = new LocationTrackingDTO();
        dto.setTimestamp(source.getInstant("timestamp"));
        dto.setLatitude(source.isNull("latitude") ? null : source.getDouble("latitude"));
        dto.setLongitude(source.isNull("longitude") ? null : source.getDouble("longitude"));
        dto.setSpeed(source.isNull("speed") ? null : source.getDouble("speed"));
        dto.setHeading(source.isNull("heading") ? null : source.getDouble("heading"));
        dto.setAccuracy(source.isNull("accuracy") ? null : source.getDouble("accuracy"));
        dto.setRideId(source.isNull("ride_id") ? null : source.getLong("ride_id"));
        dto.setNotes(source.getString("notes"));
        return dto;
    }
}
