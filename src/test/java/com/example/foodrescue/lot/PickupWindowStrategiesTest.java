package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PickupWindowStrategiesTest {

    @Test
    void preparedMeal_requiresFortyFiveMinutes() {
        PickupWindowStrategy strategy = new PreparedMealPickupWindow();

        assertEquals(FoodCategory.PREPARED_MEAL, strategy.category());
        assertEquals(45, strategy.minWindowMinutes());
    }

    @Test
    void bakery_requiresSixtyMinutes() {
        PickupWindowStrategy strategy = new BakeryPickupWindow();

        assertEquals(FoodCategory.BAKERY, strategy.category());
        assertEquals(60, strategy.minWindowMinutes());
    }

    @Test
    void vegetables_requiresNinetyMinutes() {
        PickupWindowStrategy strategy = new VegetablesPickupWindow();

        assertEquals(FoodCategory.VEGETABLES, strategy.category());
        assertEquals(90, strategy.minWindowMinutes());
    }

    @Test
    void grocery_requiresOneHundredTwentyMinutes() {
        PickupWindowStrategy strategy = new GroceryPickupWindow();

        assertEquals(FoodCategory.GROCERY, strategy.category());
        assertEquals(120, strategy.minWindowMinutes());
    }
}
