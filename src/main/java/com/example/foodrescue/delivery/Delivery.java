package com.example.foodrescue.delivery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Delivery(
        UUID lotId,
        UUID volunteerId,
        UUID destinationPointId,
        Instant pickedUpAt,
        BigDecimal pickupWeightKg,
        Instant deliveredAt,
        String confirmationCode,
        Instant confirmedAt,
        BigDecimal receivedWeightKg) {
}
