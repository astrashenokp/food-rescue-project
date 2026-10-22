package com.example.foodrescue.delivery.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record ConfirmationForm(
        @NotBlank(message = "Вкажіть код підтвердження")
        @Pattern(regexp = "^\\d{6}$", message = "Код має містити шість цифр")
        String confirmationCode,
        @NotNull(message = "Вкажіть отриману вагу")
        @DecimalMin(value = "0.0", message = "Вага не може бути від’ємною")
        BigDecimal receivedWeightKg) {
}
