package com.yuzhi.dts.platform.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BatchSizeExceededException extends RuntimeException {

    public BatchSizeExceededException(String message) {
        super(message);
    }
}
