package com.example.foodrescue.delivery;

import java.util.Optional;
import java.util.UUID;

public interface DestinationPointRepository {

    DestinationPoint save(DestinationPoint point);

    Optional<DestinationPoint> findById(UUID id);

    boolean existsByName(String name);
}
