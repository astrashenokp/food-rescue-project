package com.example.foodrescue.delivery;

import com.example.foodrescue.common.BusinessRuleException;

public class InvalidConfirmationCodeException extends BusinessRuleException {

    public InvalidConfirmationCodeException() {
        super("Неправильний код підтвердження");
    }
}
