package com.example.foodrescue.lot;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;

public class PickupWindowValidator implements ConstraintValidator<ValidPickupWindow, LotRequest> {

    @Override
    public boolean isValid(LotRequest request, ConstraintValidatorContext context) {
        Instant from = request.pickupFrom();
        Instant to = request.pickupTo();
        if (from == null || to == null) {
            return true;
        }
        return to.isAfter(from);
    }
}
