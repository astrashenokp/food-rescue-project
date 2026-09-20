package com.example.foodrescue.common;

import org.springframework.stereotype.Service;

@Service
public class LotStatusChangerImpl implements LotStatusChanger {

    private final StatusHistoryRecorder statusHistoryRecorder;

    public LotStatusChangerImpl(StatusHistoryRecorder statusHistoryRecorder) {
        this.statusHistoryRecorder = statusHistoryRecorder;
    }

    @Override
    public void transition(FoodLot lot, LotStatus next, String comment) {
        LotStatus current = lot.getStatus();
        if (!current.canTransitionTo(next)) {
            throw new InvalidLotStateException(
                    "Лот " + lot.getId() + ": перехід " + current + " → " + next + " заборонено");
        }
        lot.setStatus(next);
        statusHistoryRecorder.record(lot.getId(), current, next, comment);
    }
}
