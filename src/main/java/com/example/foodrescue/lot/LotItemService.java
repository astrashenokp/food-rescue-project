package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodItemResponse;

import java.util.List;
import java.util.UUID;

public interface LotItemService {

    FoodItemResponse addItem(UUID lotId, FoodItemRequest request);

    List<FoodItemResponse> findItems(UUID lotId);

    FoodItemResponse getItem(UUID lotId, UUID itemId);

    FoodItemResponse updateItem(UUID lotId, UUID itemId, FoodItemRequest request);

    void deleteItem(UUID lotId, UUID itemId);
}
