package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import org.springframework.stereotype.Component;

@Component
public class PreparedMealPickupWindow implements PickupWindowStrategy {

    @Override
    public FoodCategory category() {
        return FoodCategory.PREPARED_MEAL;
    }

    @Override
    public int minWindowMinutes() {
        return 45;
    }
}
