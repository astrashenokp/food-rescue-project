package com.example.foodrescue.common;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LotStore {

    private final Map<UUID, FoodLot> lots = new ConcurrentHashMap<>();

    public FoodLot save(FoodLot lot) {
        lots.put(lot.getId(), lot);
        return lot;
    }

    public Optional<FoodLot> findById(UUID id) {
        return Optional.ofNullable(lots.get(id));
    }

    public FoodLot getById(UUID id) {
        return findById(id)
                .orElseThrow(() -> new NotFoundException("Лот " + id + " не знайдено"));
    }

    public List<FoodLot> findAll() {
        return List.copyOf(lots.values());
    }
}
