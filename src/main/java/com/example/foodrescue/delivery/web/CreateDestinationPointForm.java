package com.example.foodrescue.delivery.web;

import com.example.foodrescue.common.FoodCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateDestinationPointForm(
        @NotNull(message = "Вкажіть ID організації")
        UUID organizationId,

        @NotBlank(message = "Назва є обов’язковою")
        @Size(min = 2, max = 100, message = "Назва має містити від 2 до 100 символів")
        String name,

        @NotBlank(message = "Адреса є обов’язковою")
        @Size(max = 200, message = "Адреса має містити до 200 символів")
        String address,

        @NotBlank(message = "Години роботи є обов’язковими")
        @Pattern(regexp = "^\\d{2}:\\d{2}-\\d{2}:\\d{2}$", message = "Вкажіть години роботи у форматі HH:mm-HH:mm")
        String workingHours,

        @NotEmpty(message = "Оберіть хоча б одну категорію")
        Set<FoodCategory> acceptedCategories) {
}
