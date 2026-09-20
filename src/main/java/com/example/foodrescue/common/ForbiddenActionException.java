package com.example.foodrescue.common;

import org.springframework.http.HttpStatus;

public class ForbiddenActionException extends BusinessException {

    public ForbiddenActionException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
