package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class GroceryTolerance implements WeightToleranceStrategy {

    @Override
    public FoodCategory category() {
        return FoodCategory.GROCERY;
    }

    @Override
    public BigDecimal tolerancePercent() {
        return new BigDecimal("5");
    }
}
