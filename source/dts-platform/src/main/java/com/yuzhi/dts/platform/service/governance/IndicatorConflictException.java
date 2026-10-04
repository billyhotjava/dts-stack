package com.yuzhi.dts.platform.service.governance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Indicator state conflict")
public class IndicatorConflictException extends IllegalStateException {

    public IndicatorConflictException(String message) {
        super(message);
    }
}
