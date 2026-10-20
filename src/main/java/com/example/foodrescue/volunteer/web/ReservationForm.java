package com.example.foodrescue.volunteer.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationForm(
        @NotNull(message = "Оберіть лот")
        UUID lotId) {
}
