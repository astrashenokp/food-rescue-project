package com.example.foodrescue.common;

import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FoodItemRepository extends ListCrudRepository<FoodItem, UUID> {

    List<FoodItem> findByLotId(UUID lotId);

    Optional<FoodItem> findByIdAndLotId(UUID id, UUID lotId);
}
