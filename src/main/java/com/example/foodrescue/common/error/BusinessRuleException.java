package com.example.foodrescue.common.error;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends BusinessException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
