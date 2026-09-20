package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;

import java.util.Set;
import java.util.UUID;

public record DestinationPoint(
        UUID id,
        UUID organizationId,
        String name,
        String address,
        String workingHours,
        Set<FoodCategory> acceptedCategories) {

    public DestinationPoint {
        acceptedCategories = Set.copyOf(acceptedCategories);
    }
}
