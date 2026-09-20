package com.example.foodrescue.common;

import java.time.Instant;
import java.util.UUID;

public record LotStatusHistory(UUID lotId, LotStatus fromStatus, LotStatus toStatus,
                                Instant changedAt, String comment) {
}
