package com.example.foodrescue.common.history;

import com.example.foodrescue.common.lot.LotStatus;

import java.time.Instant;
import java.util.UUID;

public record LotStatusHistory(UUID lotId, LotStatus fromStatus, LotStatus toStatus,
                                Instant changedAt, String comment) {
}
