package com.example.foodrescue.lot;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Duration;
import java.time.Instant;

public class PickupWindowValidator implements ConstraintValidator<ValidPickupWindow, LotRequest> {

    private static final Duration MIN_WINDOW = Duration.ofMinutes(45);

    @Override
    public boolean isValid(LotRequest request, ConstraintValidatorContext context) {
        Instant from = request.pickupFrom();
        Instant to = request.pickupTo();
        if (from == null || to == null) {
            return true;
        }
        return !to.isBefore(from) && Duration.between(from, to).compareTo(MIN_WINDOW) >= 0;
    }
}
