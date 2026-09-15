package com.example.foodrescue.delivery;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ConfirmationRequest(
        @NotBlank(message = "Код підтвердження обов'язковий")
        @Pattern(regexp = "^\\d{6}$", message = "Код має містити 6 цифр")
        String confirmationCode,

        @NotNull(message = "Отримана вага обов'язкова")
        @DecimalMin(value = "0.0", message = "Вага не може бути від'ємною")
        BigDecimal receivedWeightKg) {
}
