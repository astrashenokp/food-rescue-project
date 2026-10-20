package com.example.foodrescue.lot.web;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.StorageCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateLotForm(
        @NotBlank(message = "Назва є обов’язковою")
        @Size(min = 3, max = 100, message = "Назва має містити від 3 до 100 символів")
        String title,
        @NotNull(message = "Оберіть категорію")
        FoodCategory category,
        @NotBlank(message = "Назва продукту є обов’язковою")
        String itemName,
        @NotNull(message = "Вкажіть кількість")
        @DecimalMin(value = "0.1", message = "Кількість має бути не менше 0.1")
        BigDecimal itemQuantity,
        @NotNull(message = "Оберіть одиницю виміру")
        ItemUnit itemUnit,
        @NotNull(message = "Вкажіть вагу")
        @DecimalMin(value = "0.1", message = "Вага має бути не менше 0.1")
        BigDecimal totalWeightKg,
        @NotNull(message = "Оберіть умови зберігання")
        StorageCondition storageCondition,
        @NotBlank(message = "Адреса є обов’язковою")
        @Size(max = 200, message = "Адреса має містити до 200 символів")
        String pickupAddress,
        @NotNull(message = "Вкажіть початок вікна")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime pickupFrom,
        @NotNull(message = "Вкажіть кінець вікна")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime pickupTo) {
}
