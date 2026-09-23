package com.example.foodrescue.delivery;

import com.example.foodrescue.common.BusinessRuleException;
import com.example.foodrescue.common.FoodCategory;

import java.util.UUID;

public class CategoryNotAcceptedException extends BusinessRuleException {

    public CategoryNotAcceptedException(UUID destinationPointId, FoodCategory category) {
        super("Пункт призначення " + destinationPointId + " не приймає категорію " + category);
    }
}
