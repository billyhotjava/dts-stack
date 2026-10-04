package com.yuzhi.dts.platform.service.governance;

/** Stable application error exposed by the measurement-unit owner boundary. */
public class MeasurementUnitException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        UNPROCESSABLE,
        NOT_FOUND,
        CONFLICT,
        PRECONDITION_REQUIRED,
    }

    private final String code;
    private final Kind kind;
    private final Object details;

    public MeasurementUnitException(String code, String message, Kind kind) {
        this(code, message, kind, null);
    }

    public MeasurementUnitException(String code, String message, Kind kind, Object details) {
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
