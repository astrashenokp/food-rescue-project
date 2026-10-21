package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BakeryTolerance implements WeightToleranceStrategy {

    @Override
    public FoodCategory category() {
        return FoodCategory.BAKERY;
    }

    @Override
    public BigDecimal tolerancePercent() {
        return new BigDecimal("10");
    }
}
