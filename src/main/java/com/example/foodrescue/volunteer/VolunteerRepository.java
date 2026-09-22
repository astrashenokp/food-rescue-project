package com.example.foodrescue.volunteer;

import java.util.Optional;
import java.util.UUID;

public interface VolunteerRepository {

    VolunteerProfile save(VolunteerProfile volunteer);

    Optional<VolunteerProfile> findById(UUID id);
}
