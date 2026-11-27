package com.example.foodrescue.volunteer;

import com.example.foodrescue.volunteer.internal.VolunteerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class VolunteerCountReporter {

    private static final Logger log = LoggerFactory.getLogger(VolunteerCountReporter.class);

    private final VolunteerRepository volunteerRepository;

    public VolunteerCountReporter(VolunteerRepository volunteerRepository) {
        this.volunteerRepository = volunteerRepository;
    }

    @Scheduled(initialDelay = 10, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void run() {
        log.info("Кількість профілів волонтерів: {}", volunteerRepository.count());
    }
}
