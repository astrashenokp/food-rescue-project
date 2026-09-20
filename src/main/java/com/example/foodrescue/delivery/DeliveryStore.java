package com.example.foodrescue.delivery;

import com.example.foodrescue.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeliveryStore {

    private final Map<UUID, Delivery> deliveries = new ConcurrentHashMap<>();

    public Delivery save(Delivery delivery) {
        deliveries.put(delivery.lotId(), delivery);
        return delivery;
    }

    public Delivery getByLotId(UUID lotId) {
        Delivery delivery = deliveries.get(lotId);
        if (delivery == null) throw new NotFoundException("Доставку для лоту " + lotId + " не знайдено");
        return delivery;
    }
}
