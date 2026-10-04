package com.yuzhi.dts.platform.service.governance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Indicator not found")
public class IndicatorNotFoundException extends RuntimeException {

    public IndicatorNotFoundException(String message) {
        super(message);
    }
}
