package com.example.foodrescue.delivery.internal.exception;

import com.example.foodrescue.common.BusinessRuleException;

public class InvalidConfirmationCodeException extends BusinessRuleException {

    public InvalidConfirmationCodeException() {
        super("Неправильний код підтвердження");
    }
}
