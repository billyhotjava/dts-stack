package com.yuzhi.dts.platform.web.rest;

public enum ResultStatus {
    SUCCESS(200),
    ERROR(-1),
    TIMEOUT(401);

    private final int code;

    ResultStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

