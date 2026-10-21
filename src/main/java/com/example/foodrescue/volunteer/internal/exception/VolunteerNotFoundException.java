package com.example.foodrescue.volunteer.internal.exception;

import com.example.foodrescue.common.NotFoundException;

import java.util.UUID;

public class VolunteerNotFoundException extends NotFoundException {

    public VolunteerNotFoundException(UUID id) {
        super("Волонтер " + id + " не знайдено");
    }
}
