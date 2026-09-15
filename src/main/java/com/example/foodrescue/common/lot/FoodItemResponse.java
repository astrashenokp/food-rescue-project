package com.example.foodrescue.common.lot;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FoodItemResponse(String name, BigDecimal quantity, ItemUnit unit, LocalDate bestBefore) {

    public static FoodItemResponse from(FoodItem item) {
        return new FoodItemResponse(item.name(), item.quantity(), item.unit(), item.bestBefore());
    }
}
