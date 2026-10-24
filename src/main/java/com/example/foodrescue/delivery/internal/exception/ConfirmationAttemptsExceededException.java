package com.example.foodrescue.delivery.internal.exception;

import com.example.foodrescue.common.BusinessRuleException;

public class ConfirmationAttemptsExceededException extends BusinessRuleException {

    public ConfirmationAttemptsExceededException() {
        super("Перевищено кількість спроб підтвердження доставки");
    }
}
