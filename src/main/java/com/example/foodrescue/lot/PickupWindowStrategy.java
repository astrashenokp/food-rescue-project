package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;

public interface PickupWindowStrategy {

    FoodCategory category();

    int minWindowMinutes();
}
