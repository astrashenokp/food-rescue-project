package com.example.foodrescue.common.error;

import org.springframework.http.HttpStatus;

public class ForbiddenActionException extends BusinessException {

    public ForbiddenActionException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
