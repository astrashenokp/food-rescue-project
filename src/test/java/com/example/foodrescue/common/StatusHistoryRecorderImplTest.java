package com.example.foodrescue.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class StatusHistoryRecorderImplTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-20T10:00:00Z");

    @Mock
    private LotStatusHistoryRepository historyRepository;

    private StatusHistoryRecorderImpl recorder;

    @BeforeEach
    void setUp() {
        recorder = new StatusHistoryRecorderImpl(historyRepository, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void record_savesHistoryEntryWithTimeFromClock() {
        UUID lotId = UUID.randomUUID();

        recorder.record(lotId, LotStatus.DRAFT, LotStatus.PUBLISHED, "Опубліковано");

        ArgumentCaptor<LotStatusHistory> captor = ArgumentCaptor.forClass(LotStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        verifyNoMoreInteractions(historyRepository);
        LotStatusHistory entry = captor.getValue();
        assertEquals(lotId, entry.getLotId());
        assertEquals(LotStatus.DRAFT, entry.getFromStatus());
        assertEquals(LotStatus.PUBLISHED, entry.getToStatus());
        assertEquals(FIXED_INSTANT, entry.getChangedAt());
        assertEquals("Опубліковано", entry.getComment());
    }

    @Test
    void findByLot_returnsRepositoryResultInStoredOrder() {
        UUID lotId = UUID.randomUUID();
        List<LotStatusHistory> stored = List.of(
                new LotStatusHistory(lotId, LotStatus.DRAFT, LotStatus.PUBLISHED, FIXED_INSTANT, "Публікація"),
                new LotStatusHistory(lotId, LotStatus.PUBLISHED, LotStatus.RESERVED, FIXED_INSTANT, "Резерв"));
        given(historyRepository.findByLotIdOrderByIdAsc(lotId)).willReturn(stored);

        List<LotStatusHistory> result = recorder.findByLot(lotId);

        assertSame(stored, result);
        verify(historyRepository).findByLotIdOrderByIdAsc(lotId);
        verifyNoMoreInteractions(historyRepository);
    }

    @Test
    void findByLot_noEntries_returnsEmptyList() {
        UUID lotId = UUID.randomUUID();
        given(historyRepository.findByLotIdOrderByIdAsc(lotId)).willReturn(List.of());

        List<LotStatusHistory> result = recorder.findByLot(lotId);

        assertTrue(result.isEmpty());
        verify(historyRepository).findByLotIdOrderByIdAsc(lotId);
        verifyNoMoreInteractions(historyRepository);
    }
}
