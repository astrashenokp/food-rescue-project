package com.example.foodrescue.lot;

import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusHistory;
import com.example.foodrescue.common.LotStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class LotStatusHistoryRepositoryTest {

    private static final Instant SAME_TIME = Instant.parse("2026-09-20T12:00:00Z");

    @Autowired
    private LotStatusHistoryRepository historyRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByLotIdOrderByIdAsc_returnsOnlyThatLotInStoredOrderEvenWithEqualTimestamps() {
        UUID lot = UUID.randomUUID();
        UUID otherLot = UUID.randomUUID();
        historyRepository.save(new LotStatusHistory(lot, null, LotStatus.DRAFT, SAME_TIME, "Створено"));
        historyRepository.save(new LotStatusHistory(otherLot, LotStatus.DRAFT, LotStatus.PUBLISHED, SAME_TIME, "Інший лот"));
        historyRepository.save(new LotStatusHistory(lot, LotStatus.DRAFT, LotStatus.PUBLISHED, SAME_TIME, "Публікація"));
        historyRepository.save(new LotStatusHistory(lot, LotStatus.PUBLISHED, LotStatus.RESERVED, SAME_TIME, "Резерв"));
        entityManager.flush();
        entityManager.clear();

        List<LotStatusHistory> history = historyRepository.findByLotIdOrderByIdAsc(lot);

        assertEquals(List.of("Створено", "Публікація", "Резерв"),
                history.stream().map(LotStatusHistory::getComment).toList());
        assertNull(history.getFirst().getFromStatus());
        assertEquals(LotStatus.RESERVED, history.getLast().getToStatus());
    }

    @Test
    void findByLotIdOrderByIdAsc_lotWithoutHistory_returnsEmptyList() {
        assertTrue(historyRepository.findByLotIdOrderByIdAsc(UUID.randomUUID()).isEmpty());
    }
}
