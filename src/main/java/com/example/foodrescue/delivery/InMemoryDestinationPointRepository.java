package com.example.foodrescue.delivery;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDestinationPointRepository implements DestinationPointRepository {

    private final Map<UUID, DestinationPoint> points = new ConcurrentHashMap<>();

    @Override
    public DestinationPoint save(DestinationPoint point) {
        points.put(point.id(), point);
        return point;
    }

    @Override
    public Optional<DestinationPoint> findById(UUID id) {
        return Optional.ofNullable(points.get(id));
    }

    @Override
    public boolean existsByName(String name) {
        return points.values().stream().anyMatch(point -> point.name().equals(name));
    }
}
