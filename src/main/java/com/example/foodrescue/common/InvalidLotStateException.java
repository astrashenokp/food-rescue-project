package com.example.foodrescue.common;

public class InvalidLotStateException extends ConflictException {

    public InvalidLotStateException(String message) {
        super(message);
    }
}
