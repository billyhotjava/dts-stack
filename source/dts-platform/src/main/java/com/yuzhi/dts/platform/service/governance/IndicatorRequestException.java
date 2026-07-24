package com.yuzhi.dts.platform.service.governance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Invalid indicator request")
public class IndicatorRequestException extends IllegalArgumentException {

    public IndicatorRequestException(String message) {
        super(message);
    }

    public IndicatorRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
