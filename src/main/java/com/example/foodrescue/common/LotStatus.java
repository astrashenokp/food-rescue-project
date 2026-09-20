package com.example.foodrescue.common;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum LotStatus {
    DRAFT, PENDING_MODERATION, PUBLISHED, RESERVED, PICKED_UP,
    DELIVERED, CONFIRMED, DISPUTED, EXPIRED, CANCELLED, FAILED;

    private static final Map<LotStatus, Set<LotStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(LotStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(DRAFT, EnumSet.of(PENDING_MODERATION, PUBLISHED, CANCELLED));
        ALLOWED_TRANSITIONS.put(PENDING_MODERATION, EnumSet.of(PUBLISHED, DRAFT, CANCELLED));
        ALLOWED_TRANSITIONS.put(PUBLISHED, EnumSet.of(RESERVED, CANCELLED, EXPIRED));
        ALLOWED_TRANSITIONS.put(RESERVED, EnumSet.of(PUBLISHED, PICKED_UP));
        ALLOWED_TRANSITIONS.put(PICKED_UP, EnumSet.of(DELIVERED, FAILED));
        ALLOWED_TRANSITIONS.put(DELIVERED, EnumSet.of(CONFIRMED, DISPUTED));
        ALLOWED_TRANSITIONS.put(CONFIRMED, EnumSet.noneOf(LotStatus.class));
        ALLOWED_TRANSITIONS.put(DISPUTED, EnumSet.noneOf(LotStatus.class));
        ALLOWED_TRANSITIONS.put(EXPIRED, EnumSet.noneOf(LotStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(LotStatus.class));
        ALLOWED_TRANSITIONS.put(FAILED, EnumSet.noneOf(LotStatus.class));
    }

    public boolean canTransitionTo(LotStatus next) {
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }
}
