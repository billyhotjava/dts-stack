package com.yuzhi.dts.platform.service.modeling;

/** Stable error boundary for plan deployment and OPERATIONAL_RUN commands. */
public class PlanExecutionException extends RuntimeException {

    private final String code;
    private final Kind kind;

    public PlanExecutionException(String code, String message, Kind kind) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        UNAVAILABLE,
    }
}
