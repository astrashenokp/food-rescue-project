package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.ForbiddenActionException;

import java.util.UUID;

public class VolunteerRestrictedException extends ForbiddenActionException {

    public VolunteerRestrictedException(UUID volunteerId) {
        super("Волонтер " + volunteerId + " обмежений і може брати лише BAKERY та GROCERY");
    }
}
