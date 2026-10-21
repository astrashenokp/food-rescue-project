package com.example.foodrescue.lot.internal.strategy;

import com.example.foodrescue.common.FoodCategory;

public interface PickupWindowStrategy {

    FoodCategory category();

    int minWindowMinutes();
}
