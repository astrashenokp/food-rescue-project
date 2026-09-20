package com.example.foodrescue.common;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotRepository {

    FoodLot save(FoodLot lot);

    Optional<FoodLot> findById(UUID id);

    List<FoodLot> findAll();
}
