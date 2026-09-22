package com.example.foodrescue.volunteer;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryVolunteerRepository implements VolunteerRepository {

    private final Map<UUID, VolunteerProfile> volunteers = new ConcurrentHashMap<>();

    @Override
    public VolunteerProfile save(VolunteerProfile volunteer) {
        volunteers.put(volunteer.getId(), volunteer);
        return volunteer;
    }

    @Override
    public Optional<VolunteerProfile> findById(UUID id) {
        return Optional.ofNullable(volunteers.get(id));
    }
}
