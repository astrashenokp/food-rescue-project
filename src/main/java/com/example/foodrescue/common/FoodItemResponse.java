package com.example.foodrescue.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FoodItemResponse(UUID id, String name, BigDecimal quantity, ItemUnit unit, LocalDate bestBefore) {

    public static FoodItemResponse from(FoodItem item) {
        return new FoodItemResponse(
                item.getId(), item.getName(), item.getQuantity(), item.getUnit(), item.getBestBefore());
    }
}
