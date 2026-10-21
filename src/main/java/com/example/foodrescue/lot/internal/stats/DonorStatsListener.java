package com.example.foodrescue.lot;

import com.example.foodrescue.delivery.DeliveryFinishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class DonorStatsListener {
    private static final Logger log = LoggerFactory.getLogger(DonorStatsListener.class);
    private final DonorStatsService donorStatsService;

    public DonorStatsListener(DonorStatsService donorStatsService) {
        this.donorStatsService = donorStatsService;
    }

    @ApplicationModuleListener
    void on(DeliveryFinishedEvent event) {
        log.info("Отримано результат доставки для лота {}: {}", event.lotId(), event.outcome());
        donorStatsService.recordOutcome(event.donorOrgId(), event.outcome());
    }
}
