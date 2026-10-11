package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodItemResponse;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

public interface LotItemService {

    @PreAuthorize("hasRole('DONOR')")
    FoodItemResponse addItem(UUID lotId, FoodItemRequest request);

    List<FoodItemResponse> findItems(UUID lotId);

    FoodItemResponse getItem(UUID lotId, UUID itemId);

    @PreAuthorize("hasRole('DONOR')")
    FoodItemResponse updateItem(UUID lotId, UUID itemId, FoodItemRequest request);

    @PreAuthorize("hasRole('DONOR')")
    void deleteItem(UUID lotId, UUID itemId);
}
