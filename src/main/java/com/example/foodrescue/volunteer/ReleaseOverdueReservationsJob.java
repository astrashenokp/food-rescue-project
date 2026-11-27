package com.example.foodrescue.volunteer;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ReleaseOverdueReservationsJob {

    private static final Logger log = LoggerFactory.getLogger(ReleaseOverdueReservationsJob.class);

    private final VolunteerService volunteerService;

    public ReleaseOverdueReservationsJob(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @Scheduled(initialDelay = 10, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "releaseOverdueReservations", lockAtLeastFor = "25s", lockAtMostFor = "5m")
    public void run() {
        log.info("Знято прострочених резервів: {}", volunteerService.releaseOverdueReservations());
    }
}
