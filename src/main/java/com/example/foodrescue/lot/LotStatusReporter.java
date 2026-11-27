package com.example.foodrescue.lot;

import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LotStatusReporter {

    private static final Logger log = LoggerFactory.getLogger(LotStatusReporter.class);

    private final LotRepository lotRepository;

    public LotStatusReporter(LotRepository lotRepository) {
        this.lotRepository = lotRepository;
    }

    @Scheduled(initialDelay = 10, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void run() {
        log.info("Лотів PUBLISHED: {}, RESERVED: {}",
                lotRepository.countByStatus(LotStatus.PUBLISHED),
                lotRepository.countByStatus(LotStatus.RESERVED));
    }
}
