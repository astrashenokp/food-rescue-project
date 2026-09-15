package com.example.foodrescue.volunteer;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationRequest(
        @NotNull(message = "ID волонтера обов'язковий")
        UUID volunteerId
) {
}
