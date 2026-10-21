package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.NotFoundException;

import java.util.UUID;

public class VolunteerNotFoundException extends NotFoundException {

    public VolunteerNotFoundException(UUID id) {
        super("Волонтер " + id + " не знайдено");
    }
}
