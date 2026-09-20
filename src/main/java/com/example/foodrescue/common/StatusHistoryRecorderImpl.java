package com.example.foodrescue.common;

import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class StatusHistoryRecorderImpl implements StatusHistoryRecorder {

    private final List<LotStatusHistory> history = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public StatusHistoryRecorderImpl(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void record(UUID lotId, LotStatus from, LotStatus to, String comment) {
        history.add(new LotStatusHistory(lotId, from, to, Instant.now(clock), comment));
    }

    @Override
    public List<LotStatusHistory> findByLot(UUID lotId) {
        return history.stream()
                .filter(entry -> entry.lotId().equals(lotId))
                .toList();
    }
}
