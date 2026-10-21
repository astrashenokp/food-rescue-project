package com.example.foodrescue.lot;

import com.example.foodrescue.common.NotFoundException;

import java.util.UUID;

public class FoodItemNotFoundException extends NotFoundException {

    public FoodItemNotFoundException(UUID lotId, UUID itemId) {
        super("Позиція " + itemId + " не знайдена в лоті " + lotId);
    }
}
