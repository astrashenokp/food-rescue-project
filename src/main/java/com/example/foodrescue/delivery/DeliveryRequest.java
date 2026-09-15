package com.example.foodrescue.delivery;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryRequest(
        @NotNull(message = "Пункт призначення обов'язковий")
        UUID destinationPointId) {
}
