package com.example.foodrescue.volunteer;

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
class VolunteerStatsListenerTest {

    @Mock
    private VolunteerService volunteerService;

    @Test
    void on_confirmedEvent_passesDataToService() {
        UUID volunteerId = UUID.randomUUID();
        DeliveryFinishedEvent event = new DeliveryFinishedEvent(
                UUID.randomUUID(), UUID.randomUUID(), volunteerId, DeliveryOutcome.CONFIRMED, false);

        new VolunteerStatsListener(volunteerService).on(event);

        verify(volunteerService).recordOutcome(volunteerId, DeliveryOutcome.CONFIRMED, false);
        verifyNoMoreInteractions(volunteerService);
    }

    @Test
    void on_disputedEventWithLatePickup_passesAllDataToService() {
        UUID volunteerId = UUID.randomUUID();
        DeliveryFinishedEvent event = new DeliveryFinishedEvent(
                UUID.randomUUID(), UUID.randomUUID(), volunteerId, DeliveryOutcome.DISPUTED, true);

        new VolunteerStatsListener(volunteerService).on(event);

        verify(volunteerService).recordOutcome(volunteerId, DeliveryOutcome.DISPUTED, true);
        verifyNoMoreInteractions(volunteerService);
    }
}
