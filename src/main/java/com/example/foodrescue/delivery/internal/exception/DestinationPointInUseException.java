package com.example.foodrescue.delivery.internal.exception;

import com.example.foodrescue.common.ConflictException;

import java.util.UUID;

public class DestinationPointInUseException extends ConflictException {

    public DestinationPointInUseException(UUID id) {
        super("Пункт призначення " + id + " використовується у доставці");
    }
}
