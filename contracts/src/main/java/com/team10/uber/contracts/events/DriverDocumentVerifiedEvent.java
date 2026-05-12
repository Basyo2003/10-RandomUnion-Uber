package com.team10.uber.contracts.events;

public record DriverDocumentVerifiedEvent(
    Long driverId,
    Long documentId,
    Long verifiedBy
) {}