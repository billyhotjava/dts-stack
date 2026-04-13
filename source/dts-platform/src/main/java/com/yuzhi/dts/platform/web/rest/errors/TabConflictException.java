package com.yuzhi.dts.platform.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class TabConflictException extends RuntimeException {

    public TabConflictException(String message) {
        super(message);
    }
}
