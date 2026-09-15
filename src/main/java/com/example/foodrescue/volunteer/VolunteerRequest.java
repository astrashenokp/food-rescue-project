package com.example.foodrescue.volunteer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VolunteerRequest(
        @NotBlank(message = "Ім'я обов'язкове")
        @Size(min = 2, max = 100, message = "Ім'я від 2 до 100 символів")
        String fullName,

        @NotBlank(message = "Email обов'язковий")
        @Email(message = "Невірний формат email")
        String email,

        @NotBlank(message = "Телефон обов'язковий")
        @Pattern(regexp = "^\\+380\\d{9}$", message = "Формат: +380XXXXXXXXX")
        String phone,

        @NotNull(message = "Тип транспорту обов'язковий")
        TransportType transportType,

        @NotBlank(message = "Зона активності обов'язкова")
        @Size(max = 100, message = "Зона активності максимум 100 символів")
        String activityZone
) {
}
