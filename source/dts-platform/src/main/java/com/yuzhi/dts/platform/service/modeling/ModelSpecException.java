package com.yuzhi.dts.platform.service.modeling;

/** Stable application error exposed by the canonical ModelSpec boundary. */
public class ModelSpecException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        UNPROCESSABLE,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        PRECONDITION_REQUIRED,
    }

    private final String code;
    private final Kind kind;
    private final Object details;

    public ModelSpecException(String code, String message, Kind kind) {
        this(code, message, kind, null);
    }

    public ModelSpecException(String code, String message, Kind kind, Object details) {
        super(message);
        this.code = code;
        this.kind = kind;
        this.details = details;
    }

    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }

    public Object details() {
        return details;
    }
}
