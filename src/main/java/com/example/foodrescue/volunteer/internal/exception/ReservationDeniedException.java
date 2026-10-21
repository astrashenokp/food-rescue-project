package com.example.foodrescue.volunteer.internal.exception;

import com.example.foodrescue.common.ForbiddenActionException;

public class ReservationDeniedException extends ForbiddenActionException {

    public ReservationDeniedException(String message) {
        super(message);
    }
}
