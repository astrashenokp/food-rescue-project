package com.example.foodrescue.delivery.internal.exception;

import com.example.foodrescue.common.ConflictException;

public class DuplicateDestinationPointException extends ConflictException {

    public DuplicateDestinationPointException(String name) {
        super("Пункт призначення з назвою '" + name + "' вже існує");
    }
}
