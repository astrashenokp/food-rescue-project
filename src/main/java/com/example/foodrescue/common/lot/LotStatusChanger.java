package com.example.foodrescue.common.lot;

import com.example.foodrescue.common.error.ConflictException;
import com.example.foodrescue.common.history.StatusHistoryRecorder;
import org.springframework.stereotype.Component;

@Component
public class LotStatusChanger {

    private final StatusHistoryRecorder statusHistoryRecorder;

    public LotStatusChanger(StatusHistoryRecorder statusHistoryRecorder) {
        this.statusHistoryRecorder = statusHistoryRecorder;
    }

    public void transition(FoodLot lot, LotStatus next, String comment) {
        LotStatus current = lot.getStatus();
        if (!current.canTransitionTo(next)) {
            throw new ConflictException(
                    "Лот " + lot.getId() + ": перехід " + current + " → " + next + " заборонено");
        }
        lot.setStatus(next);
        statusHistoryRecorder.record(lot.getId(), current, next, comment);
    }
}
