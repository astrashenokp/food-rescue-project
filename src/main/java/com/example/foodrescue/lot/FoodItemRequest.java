package com.example.foodrescue.lot;

import com.example.foodrescue.common.ItemUnit;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FoodItemRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal quantity,
        @NotNull ItemUnit unit,
        @FutureOrPresent LocalDate bestBefore) {
}
