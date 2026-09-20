package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VolunteerStore {

    private final Map<UUID, VolunteerProfile> volunteers = new ConcurrentHashMap<>();

    public VolunteerProfile save(VolunteerProfile volunteer) {
        volunteers.put(volunteer.getId(), volunteer);
        return volunteer;
    }

    public Optional<VolunteerProfile> findById(UUID id) {
        return Optional.ofNullable(volunteers.get(id));
    }

    public VolunteerProfile getById(UUID id) {
        return findById(id)
                .orElseThrow(() -> new NotFoundException("Волонтер " + id + " не знайдено"));
    }
}
