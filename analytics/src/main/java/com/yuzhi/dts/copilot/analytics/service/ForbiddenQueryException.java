package com.yuzhi.dts.copilot.analytics.service;

public class ForbiddenQueryException extends RuntimeException {

    public ForbiddenQueryException(String message) {
        super(message);
    }
}
