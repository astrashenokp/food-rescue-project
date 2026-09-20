package com.example.foodrescue.lot;

import com.example.foodrescue.common.ConflictException;

import java.util.UUID;

public class LotNotDraftException extends ConflictException {

    public LotNotDraftException(UUID lotId) {
        super("Лот " + lotId + " не в чернетці");
    }
}
