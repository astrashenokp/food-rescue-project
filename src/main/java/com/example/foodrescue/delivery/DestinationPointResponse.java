package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;

import java.util.Set;
import java.util.UUID;

public record DestinationPointResponse(
        UUID id,
        UUID organizationId,
        String name,
        String address,
        String workingHours,
        Set<FoodCategory> acceptedCategories) {

    public static DestinationPointResponse from(DestinationPoint point) {
        return new DestinationPointResponse(
                point.getId(),
                point.getOrganizationId(),
                point.getName(),
                point.getAddress(),
                point.getWorkingHours(),
                Set.copyOf(point.getAcceptedCategories()));
    }
}
