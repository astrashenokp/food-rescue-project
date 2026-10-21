package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.ConflictException;

public class DuplicateVolunteerException extends ConflictException {

    public DuplicateVolunteerException(String email) {
        super("Волонтер з email " + email + " вже існує");
    }
}
