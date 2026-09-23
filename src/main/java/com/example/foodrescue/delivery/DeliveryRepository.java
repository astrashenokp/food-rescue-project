package com.example.foodrescue.delivery;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository {

    Delivery save(Delivery delivery);

    Optional<Delivery> findByLotId(UUID lotId);
}
