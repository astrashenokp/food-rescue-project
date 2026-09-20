package com.example.foodrescue.lot;

import com.example.foodrescue.delivery.DeliveryFinishedEvent;
import com.example.foodrescue.delivery.DeliveryOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DonorStatsListenerTest {

    @Mock
    private DonorStatsService donorStatsService;

    @Test
    void on_confirmedEvent_passesDonorAndOutcomeToService() {
        UUID donorId = UUID.randomUUID();
        DeliveryFinishedEvent event = new DeliveryFinishedEvent(
                UUID.randomUUID(), donorId, UUID.randomUUID(), DeliveryOutcome.CONFIRMED, false);

        new DonorStatsListener(donorStatsService).on(event);

        verify(donorStatsService).recordOutcome(donorId, DeliveryOutcome.CONFIRMED);
        verifyNoMoreInteractions(donorStatsService);
    }

    @Test
    void on_disputedEvent_passesDonorAndOutcomeToService() {
        UUID donorId = UUID.randomUUID();
        DeliveryFinishedEvent event = new DeliveryFinishedEvent(
                UUID.randomUUID(), donorId, UUID.randomUUID(), DeliveryOutcome.DISPUTED, true);

        new DonorStatsListener(donorStatsService).on(event);

        verify(donorStatsService).recordOutcome(donorId, DeliveryOutcome.DISPUTED);
        verifyNoMoreInteractions(donorStatsService);
    }
}
