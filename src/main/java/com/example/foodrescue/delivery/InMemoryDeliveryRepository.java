package com.example.foodrescue.delivery;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDeliveryRepository implements DeliveryRepository {

    private final Map<UUID, Delivery> deliveries = new ConcurrentHashMap<>();

    @Override
    public Delivery save(Delivery delivery) {
        deliveries.put(delivery.lotId(), delivery);
        return delivery;
    }

    @Override
    public Optional<Delivery> findByLotId(UUID lotId) {
        return Optional.ofNullable(deliveries.get(lotId));
    }
}
