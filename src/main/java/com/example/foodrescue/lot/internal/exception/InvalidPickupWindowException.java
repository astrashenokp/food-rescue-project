package com.example.foodrescue.lot.internal.exception;

import com.example.foodrescue.common.BusinessRuleException;

public class InvalidPickupWindowException extends BusinessRuleException {

    public InvalidPickupWindowException(String message) {
        super(message);
    }
}
