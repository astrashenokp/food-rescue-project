package com.example.foodrescue.common;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FoodItem(String name, BigDecimal quantity, ItemUnit unit, LocalDate bestBefore) {
}
