package com.yuzhi.dts.platform.service.etl;

/**
 * The configured dbt runtime target cannot be used. Extends {@link IllegalStateException} so
 * existing callers keep their handling while runtime callbacks can surface the stable code.
 */
public class DbtRuntimeTargetException extends IllegalStateException {

    private final String code;

    public DbtRuntimeTargetException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        this.code = code.trim();
    }

    public String code() {
        return code;
    }
}
