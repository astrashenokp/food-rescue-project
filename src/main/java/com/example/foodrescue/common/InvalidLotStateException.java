package com.example.foodrescue.common;

public class InvalidLotStateException extends BusinessRuleException {

    public InvalidLotStateException(String message) {
        super(message);
    }
}
