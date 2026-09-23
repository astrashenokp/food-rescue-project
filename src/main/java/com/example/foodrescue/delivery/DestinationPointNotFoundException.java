package com.example.foodrescue.delivery;

import com.example.foodrescue.common.NotFoundException;

import java.util.UUID;

public class DestinationPointNotFoundException extends NotFoundException {

    public DestinationPointNotFoundException(UUID id) {
        super("Пункт призначення " + id + " не знайдено");
    }
}
