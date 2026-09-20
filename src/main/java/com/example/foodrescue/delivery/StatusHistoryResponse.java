package com.example.foodrescue.delivery;

import com.example.foodrescue.common.LotStatus;

import java.time.Instant;

public record StatusHistoryResponse(
        LotStatus fromStatus,
        LotStatus toStatus,
        Instant changedAt,
        String comment) {
}
