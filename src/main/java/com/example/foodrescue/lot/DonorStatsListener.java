package com.example.foodrescue.lot;

import com.example.foodrescue.delivery.DeliveryFinishedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class DonorStatsListener {

    private final DonorStatsService donorStatsService;

    public DonorStatsListener(DonorStatsService donorStatsService) {
        this.donorStatsService = donorStatsService;
    }

    @ApplicationModuleListener
    void on(DeliveryFinishedEvent event) {
        System.out.println("[" + Thread.currentThread().getName() + "] donor stats: lot " + event.lotId() + " " + event.outcome());
        donorStatsService.recordOutcome(event.donorOrgId(), event.outcome());
    }
}
