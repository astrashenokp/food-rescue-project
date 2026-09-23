package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightToleranceStrategiesTest {

    @Test
    void preparedMeal_returnsTenPercent() {
        WeightToleranceStrategy strategy = new PreparedMealTolerance();

        assertEquals(FoodCategory.PREPARED_MEAL, strategy.category());
        assertEquals(0, new BigDecimal("10").compareTo(strategy.tolerancePercent()));
    }

    @Test
    void bakery_returnsTenPercent() {
        WeightToleranceStrategy strategy = new BakeryTolerance();

        assertEquals(FoodCategory.BAKERY, strategy.category());
        assertEquals(0, new BigDecimal("10").compareTo(strategy.tolerancePercent()));
    }

    @Test
    void vegetables_returnsFifteenPercent() {
        WeightToleranceStrategy strategy = new VegetablesTolerance();

        assertEquals(FoodCategory.VEGETABLES, strategy.category());
        assertEquals(0, new BigDecimal("15").compareTo(strategy.tolerancePercent()));
    }

    @Test
    void grocery_returnsFivePercent() {
        WeightToleranceStrategy strategy = new GroceryTolerance();

        assertEquals(FoodCategory.GROCERY, strategy.category());
        assertEquals(0, new BigDecimal("5").compareTo(strategy.tolerancePercent()));
    }
}
