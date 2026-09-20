package com.example.foodrescue.common;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class StatusHistoryRecorder {

    private final List<LotStatusHistory> history = new ArrayList<>();

    public void record(UUID lotId, LotStatus from, LotStatus to, String comment) {
        history.add(new LotStatusHistory(lotId, from, to, Instant.now(), comment));
    }

    public List<LotStatusHistory> findByLot(UUID lotId) {
        return history.stream()
                .filter(entry -> entry.lotId().equals(lotId))
                .collect(Collectors.toList());
    }
}
