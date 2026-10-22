package com.example.foodrescue.delivery.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeliverForm(
        @NotNull(message = "Оберіть пункт призначення")
        UUID destinationPointId) {
}
