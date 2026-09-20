package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record DestinationPointRequest(
        @NotNull(message = "Організація обов'язкова")
        UUID organizationId,

        @NotBlank(message = "Назва обов'язкова")
        @Size(min = 2, max = 100, message = "Назва від 2 до 100 символів")
        String name,

        @NotBlank(message = "Адреса обов'язкова")
        @Size(max = 200, message = "Адреса максимум 200 символів")
        String address,

        @NotBlank(message = "Години роботи обов'язкові")
        @Pattern(regexp = "^\\d{2}:\\d{2}-\\d{2}:\\d{2}$", message = "Формат часу HH:mm-HH:mm")
        String workingHours,

        @NotEmpty(message = "Потрібна хоча б одна категорія")
        Set<FoodCategory> acceptedCategories) {
}
