package com.example.foodrescue.delivery;

import com.example.foodrescue.common.ConflictException;

public class DuplicateDestinationPointException extends ConflictException {

    public DuplicateDestinationPointException(String name) {
        super("Пункт призначення з назвою '" + name + "' вже існує");
    }
}
