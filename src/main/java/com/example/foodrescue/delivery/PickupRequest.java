package com.example.foodrescue.delivery;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PickupRequest(
        @NotNull(message = "Фактична вага обов'язкова")
        @DecimalMin(value = "0.1", message = "Вага мінімум 0.1 кг")
        BigDecimal actualWeightKg) {
}
