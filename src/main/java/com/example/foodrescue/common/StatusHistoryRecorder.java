package com.example.foodrescue.common;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRecorder {

    void record(UUID lotId, LotStatus from, LotStatus to, String comment);

    List<LotStatusHistory> findByLot(UUID lotId);
}
