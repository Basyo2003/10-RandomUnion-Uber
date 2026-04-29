package com.team10.uber.user.adapter;

import com.team10.uber.user.dto.UserRideSummaryDTO;

public class ObjectArrayDtoAdapter {
    public UserRideSummaryDTO adapt(Object[] row) {
        throw new UnsupportedOperationException("Use adapt(Long userId, String name, Object[] row)");
    }

    public UserRideSummaryDTO adapt(Long userId, String name, Object[] row) {
        Long totalRides     = row[0] == null ? 0L : ((Number) row[0]).longValue();
        Long completedRides = row[1] == null ? 0L : ((Number) row[1]).longValue();
        Long cancelledRides = row[2] == null ? 0L : ((Number) row[2]).longValue();
        Double totalSpent   = row[3] == null ? 0.0 : ((Number) row[3]).doubleValue();
        Double averageFare  = row[4] == null ? 0.0 : ((Number) row[4]).doubleValue();
        return new UserRideSummaryDTO(userId, name, totalRides, completedRides, cancelledRides, totalSpent, averageFare);
    }
}
