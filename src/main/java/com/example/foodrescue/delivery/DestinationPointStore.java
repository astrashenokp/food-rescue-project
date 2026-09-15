package com.example.foodrescue.delivery;

import com.example.foodrescue.common.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DestinationPointStore {

    private final Map<UUID, DestinationPoint> points = new ConcurrentHashMap<>();

    public DestinationPoint save(DestinationPoint point) {
        points.put(point.id(), point);
        return point;
    }

    public DestinationPoint getById(UUID id) {
        DestinationPoint point = points.get(id);
        if (point == null) throw new NotFoundException("Пункт призначення " + id + " не знайдено");
        return point;
    }
}
