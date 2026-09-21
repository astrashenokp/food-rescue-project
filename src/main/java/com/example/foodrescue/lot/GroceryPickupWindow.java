package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import org.springframework.stereotype.Component;

@Component
public class GroceryPickupWindow implements PickupWindowStrategy {

    @Override
    public FoodCategory category() {
        return FoodCategory.GROCERY;
    }

    @Override
    public int minWindowMinutes() {
        return 120;
    }
}
