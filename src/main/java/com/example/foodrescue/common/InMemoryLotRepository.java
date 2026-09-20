package com.example.foodrescue.common;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryLotRepository implements LotRepository {

    private final Map<UUID, FoodLot> lots = new ConcurrentHashMap<>();

    @Override
    public FoodLot save(FoodLot lot) {
        lots.put(lot.getId(), lot);
        return lot;
    }

    @Override
    public Optional<FoodLot> findById(UUID id) {
        return Optional.ofNullable(lots.get(id));
    }

    @Override
    public List<FoodLot> findAll() {
        return List.copyOf(lots.values());
    }
}
