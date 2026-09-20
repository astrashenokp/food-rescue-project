package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import org.springframework.stereotype.Component;

@Component
public class VegetablesPickupWindow implements PickupWindowStrategy {

    @Override
    public FoodCategory category() {
        return FoodCategory.VEGETABLES;
    }

    @Override
    public int minWindowMinutes() {
        return 90;
    }
}
