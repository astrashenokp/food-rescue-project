package com.example.foodrescue.lot;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = PickupWindowValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPickupWindow {

    String message() default "pickupTo має бути пізніше за pickupFrom";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
