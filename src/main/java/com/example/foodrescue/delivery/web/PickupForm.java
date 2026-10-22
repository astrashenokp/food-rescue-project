package com.example.foodrescue.delivery.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PickupForm(
        @NotNull(message = "Вкажіть фактичну вагу")
        @DecimalMin(value = "0.1", message = "Вага має бути не менше 0.1 кг")
        BigDecimal actualWeightKg) {
}
