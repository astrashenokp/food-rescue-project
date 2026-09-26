package com.example.foodrescue.delivery;

import com.example.foodrescue.common.NotFoundException;

import java.util.UUID;

public class DeliveryNotFoundException extends NotFoundException {

    public DeliveryNotFoundException(UUID id) {
        super("Доставку " + id + " не знайдено");
    }
}
