package com.example.foodrescue.volunteer;

import com.example.foodrescue.delivery.DeliveryFinishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class VolunteerStatsListener {
    private static final Logger log = LoggerFactory.getLogger(VolunteerStatsListener.class);
    private final VolunteerService volunteerService;

    public VolunteerStatsListener(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @ApplicationModuleListener
    void on(DeliveryFinishedEvent event) {
        log.debug("Отримано результат доставки для лота {}: {}", event.lotId(), event.outcome());
        volunteerService.recordOutcome(event.volunteerId(), event.outcome(), event.latePickup());
    }
}
