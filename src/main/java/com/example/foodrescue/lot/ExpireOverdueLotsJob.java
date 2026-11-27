package com.example.foodrescue.lot;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ExpireOverdueLotsJob {

    private static final Logger log = LoggerFactory.getLogger(ExpireOverdueLotsJob.class);

    private final LotService lotService;

    public ExpireOverdueLotsJob(LotService lotService) {
        this.lotService = lotService;
    }

    @Scheduled(initialDelay = 10, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "expireOverdueLots", lockAtLeastFor = "25s", lockAtMostFor = "5m")
    public void run() {
        log.info("Протерміновано лотів: {}", lotService.expireOverdue());
    }
}
