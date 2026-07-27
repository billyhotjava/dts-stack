package com.yuzhi.dts.platform.service.etl;

/** Stable fail-closed error from the task-scoped dbt runtime profile boundary. */
public class DbtRuntimeProfileException extends RuntimeException {

    private final String code;

    public DbtRuntimeProfileException(String code, String message) {
        super(message);
        this.code = code;
    }

    public DbtRuntimeProfileException(
        String code,
        String message,
        Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
