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

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getLot().getId(),
                delivery.getVolunteerId(),
                delivery.getDestinationPoint() == null ? null : delivery.getDestinationPoint().getId(),
                delivery.getLot().getStatus(),
                delivery.getPickedUpAt(),
                delivery.getPickupWeightKg(),
                delivery.getDeliveredAt(),
                delivery.getConfirmationCode(),
                delivery.getConfirmedAt(),
                delivery.getReceivedWeightKg());
    }
}
