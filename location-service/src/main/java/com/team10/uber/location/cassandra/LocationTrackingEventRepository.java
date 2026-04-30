package com.team10.uber.location.cassandra;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LocationTrackingEventRepository
        extends CassandraRepository<LocationTrackingEvent, LocationTrackingEventKey> {

    List<LocationTrackingEvent> findByKeyDriverId(Long driverId);

    List<LocationTrackingEvent> findByKeyDriverIdAndKeyTimestampBetween(
            Long driverId,
            Instant start,
            Instant end
    );
}