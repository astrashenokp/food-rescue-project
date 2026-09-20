package com.example.foodrescue.delivery;

import java.util.UUID;

public record DeliveryFinishedEvent(
        UUID lotId,
        UUID donorOrgId,
        UUID volunteerId,
        DeliveryOutcome outcome,
        boolean latePickup) {
}
