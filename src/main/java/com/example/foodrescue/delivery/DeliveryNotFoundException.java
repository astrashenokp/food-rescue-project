package com.example.foodrescue.delivery;

import com.example.foodrescue.common.NotFoundException;

import java.util.UUID;

public class DeliveryNotFoundException extends NotFoundException {

    public DeliveryNotFoundException(UUID lotId) {
        super("Доставку для лоту " + lotId + " не знайдено");
    }
}
