package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.ConflictException;

import java.util.UUID;

public class VolunteerHasActiveLotsException extends ConflictException {

    public VolunteerHasActiveLotsException(UUID volunteerId) {
        super("Волонтер " + volunteerId + " має активні лоти (RESERVED/PICKED_UP), видалення неможливе");
    }
}
