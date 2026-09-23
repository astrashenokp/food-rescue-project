package com.example.foodrescue.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusHistoryRecorderImplTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-20T10:00:00Z");

    private StatusHistoryRecorderImpl recorder;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        recorder = new StatusHistoryRecorderImpl(fixedClock);
    }

    @Test
    void record_addsHistoryEntryWithFixedTime() {
        UUID lotId = UUID.randomUUID();

        recorder.record(lotId, LotStatus.DRAFT, LotStatus.PUBLISHED, "Опубліковано");

        List<LotStatusHistory> history = recorder.findByLot(lotId);
        assertEquals(1, history.size());
        LotStatusHistory entry = history.getFirst();
        assertEquals(lotId, entry.lotId());
        assertEquals(LotStatus.DRAFT, entry.fromStatus());
        assertEquals(LotStatus.PUBLISHED, entry.toStatus());
        assertEquals(FIXED_INSTANT, entry.changedAt());
        assertEquals("Опубліковано", entry.comment());
    }

    @Test
    void findByLot_filtersOnlyRequestedLot() {
        UUID lot1 = UUID.randomUUID();
        UUID lot2 = UUID.randomUUID();

        recorder.record(lot1, LotStatus.DRAFT, LotStatus.PUBLISHED, "Лот 1");
        recorder.record(lot2, LotStatus.DRAFT, LotStatus.PUBLISHED, "Лот 2");
        recorder.record(lot1, LotStatus.PUBLISHED, LotStatus.RESERVED, "Лот 1 резерв");

        List<LotStatusHistory> historyLot1 = recorder.findByLot(lot1);
        List<LotStatusHistory> historyLot2 = recorder.findByLot(lot2);

        assertEquals(2, historyLot1.size());
        assertEquals(1, historyLot2.size());
        assertTrue(historyLot1.stream().allMatch(h -> h.lotId().equals(lot1)));
        assertTrue(historyLot2.stream().allMatch(h -> h.lotId().equals(lot2)));
    }

    @Test
    void findByLot_noEntries_returnsEmptyList() {
        UUID lotId = UUID.randomUUID();

        List<LotStatusHistory> history = recorder.findByLot(lotId);

        assertTrue(history.isEmpty());
    }

    @Test
    void record_multipleTransitionsSameLot_preservesOrder() {
        UUID lotId = UUID.randomUUID();

        recorder.record(lotId, LotStatus.DRAFT, LotStatus.PUBLISHED, "Публікація");
        recorder.record(lotId, LotStatus.PUBLISHED, LotStatus.RESERVED, "Резерв");
        recorder.record(lotId, LotStatus.RESERVED, LotStatus.PICKED_UP, "Забрано");

        List<LotStatusHistory> history = recorder.findByLot(lotId);
        assertEquals(3, history.size());
        assertEquals(LotStatus.DRAFT, history.get(0).fromStatus());
        assertEquals(LotStatus.PUBLISHED, history.get(0).toStatus());
        assertEquals(LotStatus.PUBLISHED, history.get(1).fromStatus());
        assertEquals(LotStatus.RESERVED, history.get(1).toStatus());
        assertEquals(LotStatus.RESERVED, history.get(2).fromStatus());
        assertEquals(LotStatus.PICKED_UP, history.get(2).toStatus());
    }
}
