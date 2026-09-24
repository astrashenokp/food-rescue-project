package com.example.foodrescue.common;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StatusHistoryRecorderImpl implements StatusHistoryRecorder {

    private final LotStatusHistoryRepository historyRepository;
    private final Clock clock;

    public StatusHistoryRecorderImpl(LotStatusHistoryRepository historyRepository, Clock clock) {
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    @Override
    public void record(UUID lotId, LotStatus from, LotStatus to, String comment) {
        historyRepository.save(new LotStatusHistory(lotId, from, to, Instant.now(clock), comment));
    }

    @Override
    public List<LotStatusHistory> findByLot(UUID lotId) {
        return historyRepository.findByLotIdOrderByIdAsc(lotId);
    }
}
