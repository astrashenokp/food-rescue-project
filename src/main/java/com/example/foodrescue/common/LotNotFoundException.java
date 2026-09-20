package com.example.foodrescue.common;

import java.util.UUID;

public class LotNotFoundException extends NotFoundException {

    public LotNotFoundException(UUID id) {
        super("Лот " + id + " не знайдено");
    }
}
