package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;

import java.math.BigDecimal;

public interface WeightToleranceStrategy {

    FoodCategory category();

    BigDecimal tolerancePercent();
}
