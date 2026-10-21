package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import org.springframework.stereotype.Component;

@Component
public class BakeryPickupWindow implements PickupWindowStrategy {

    @Override
    public FoodCategory category() {
        return FoodCategory.BAKERY;
    }

    @Override
    public int minWindowMinutes() {
        return 60;
    }
}
