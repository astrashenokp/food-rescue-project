package com.example.foodrescue.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LotStatusChangerImplTest {

    @Mock
    private StatusHistoryRecorder statusHistoryRecorder;

    private LotStatusChangerImpl statusChanger;

    @BeforeEach
    void setUp() {
        statusChanger = new LotStatusChangerImpl(statusHistoryRecorder);
    }

    private FoodLot createLot(LotStatus status) {
        FoodLot lot = new FoodLot();
        lot.setId(UUID.randomUUID());
        lot.setStatus(status);
        return lot;
    }

    @Test
    void transition_validTransition_updatesStatusAndRecordsHistory() {
        FoodLot lot = createLot(LotStatus.DRAFT);
        String comment = "Опубліковано";

        statusChanger.transition(lot, LotStatus.PUBLISHED, comment);

        assertEquals(LotStatus.PUBLISHED, lot.getStatus());
        verify(statusHistoryRecorder).record(lot.getId(), LotStatus.DRAFT, LotStatus.PUBLISHED, comment);
        verifyNoMoreInteractions(statusHistoryRecorder);
    }

    @Test
    void transition_publishedToReserved_updatesStatusAndRecordsHistory() {
        FoodLot lot = createLot(LotStatus.PUBLISHED);
        String comment = "Зарезервовано";

        statusChanger.transition(lot, LotStatus.RESERVED, comment);

        assertEquals(LotStatus.RESERVED, lot.getStatus());
        verify(statusHistoryRecorder).record(lot.getId(), LotStatus.PUBLISHED, LotStatus.RESERVED, comment);
        verifyNoMoreInteractions(statusHistoryRecorder);
    }

    @Test
    void transition_invalidTransition_throwsInvalidLotStateExceptionAndDoesNotChangeStatus() {
        FoodLot lot = createLot(LotStatus.DRAFT);

        assertThrows(InvalidLotStateException.class,
                () -> statusChanger.transition(lot, LotStatus.DELIVERED, "Невалідний перехід"));

        assertEquals(LotStatus.DRAFT, lot.getStatus());
        verifyNoInteractions(statusHistoryRecorder);
    }

    @Test
    void transition_fromTerminalState_throwsInvalidLotStateException() {
        FoodLot lot = createLot(LotStatus.CONFIRMED);

        assertThrows(InvalidLotStateException.class,
                () -> statusChanger.transition(lot, LotStatus.DRAFT, "Спроба змінити фінальний статус"));

        assertEquals(LotStatus.CONFIRMED, lot.getStatus());
        verifyNoInteractions(statusHistoryRecorder);
    }
}
