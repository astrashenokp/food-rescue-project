package com.example.foodrescue.delivery;

import com.example.foodrescue.common.LotStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID lotId,
        UUID volunteerId,
        UUID destinationPointId,
        LotStatus lotStatus,
        Instant pickedUpAt,
        BigDecimal pickupWeightKg,
        Instant deliveredAt,
        String confirmationCode,
        Instant confirmedAt,
        BigDecimal receivedWeightKg) {
}
