package com.yuzhi.dts.platform.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyTabsException extends RuntimeException {

    public TooManyTabsException() {
        super("TOO_MANY_TABS");
    }
}
