package com.example.foodrescue.delivery.internal.strategy;

import com.example.foodrescue.common.FoodCategory;

import java.math.BigDecimal;

public interface WeightToleranceStrategy {

    FoodCategory category();

    BigDecimal tolerancePercent();
}
