package com.example.foodrescue.common.lot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LotResponse(
        UUID id, UUID donorOrgId, String title, FoodCategory category,
        List<FoodItemResponse> items, BigDecimal totalWeightKg,
        StorageCondition storageCondition, String pickupAddress,
        Instant pickupFrom, Instant pickupTo, LotStatus status,
        Instant createdAt, Instant publishedAt,
        UUID reservedByVolunteerId, Instant reservedUntil) {

    public static LotResponse from(FoodLot lot) {
        List<FoodItemResponse> items = lot.getItems() == null
                ? List.of()
                : lot.getItems().stream().map(FoodItemResponse::from).toList();

        return new LotResponse(
                lot.getId(), lot.getDonorOrgId(), lot.getTitle(), lot.getCategory(),
                items, lot.getTotalWeightKg(),
                lot.getStorageCondition(), lot.getPickupAddress(),
                lot.getPickupFrom(), lot.getPickupTo(), lot.getStatus(),
                lot.getCreatedAt(), lot.getPublishedAt(),
                lot.getReservedByVolunteerId(), lot.getReservedUntil());
    }
}
