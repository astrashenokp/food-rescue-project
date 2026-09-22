package com.example.foodrescue.volunteer;

import com.example.foodrescue.delivery.DeliveryFinishedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class VolunteerStatsListener {

    private final VolunteerService volunteerService;

    public VolunteerStatsListener(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @ApplicationModuleListener
    void on(DeliveryFinishedEvent event) {
        System.out.println("[" + Thread.currentThread().getName() + "] volunteer stats: lot " + event.lotId() + " " + event.outcome());
        volunteerService.recordOutcome(event.volunteerId(), event.outcome(), event.latePickup());
    }
}
