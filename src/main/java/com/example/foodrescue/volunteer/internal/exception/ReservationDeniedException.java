package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.ForbiddenActionException;

public class ReservationDeniedException extends ForbiddenActionException {

    public ReservationDeniedException(String message) {
        super(message);
    }
}
