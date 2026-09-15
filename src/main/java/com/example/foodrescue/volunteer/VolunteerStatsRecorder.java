package com.example.foodrescue.volunteer;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VolunteerStatsRecorder {

    private final VolunteerStore volunteerStore;

    public VolunteerStatsRecorder(VolunteerStore volunteerStore) {
        this.volunteerStore = volunteerStore;
    }

    /**
     * Записує факт передачі. Якщо late = true, збільшує лічильник запізнень.
     */
    public void recordPickup(UUID volunteerId, boolean late) {
        VolunteerProfile profile = volunteerStore.getById(volunteerId);
        if (late) {
            profile.setLatePickups(profile.getLatePickups() + 1);
        }
        volunteerStore.save(profile);
    }

    /**
     * Записує успішне підтвердження доставки.
     */
    public void recordCompletedDelivery(UUID volunteerId) {
        VolunteerProfile profile = volunteerStore.getById(volunteerId);
        profile.setCompletedDeliveries(profile.getCompletedDeliveries() + 1);
        volunteerStore.save(profile);
    }

    /**
     * Записує зрив (розбіжність у вазі → DISPUTED).
     */
    public void recordStrike(UUID volunteerId) {
        VolunteerProfile profile = volunteerStore.getById(volunteerId);
        profile.setNoShows(profile.getNoShows() + 1);
        volunteerStore.save(profile);
    }
}
