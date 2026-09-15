package com.example.foodrescue.volunteer;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VolunteerStatsRecorder {

    public void recordPickup(UUID volunteerId, boolean late) {
    }

    public void recordCompletedDelivery(UUID volunteerId) {
    }

    public void recordStrike(UUID volunteerId) {
    }
}
