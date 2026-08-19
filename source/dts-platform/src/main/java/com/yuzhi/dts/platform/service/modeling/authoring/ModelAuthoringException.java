package com.yuzhi.dts.platform.service.modeling.authoring;

import java.util.Map;

/** Stable error boundary for the unified model authoring facade. */
public class ModelAuthoringException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        PRECONDITION_FAILED,
        UNPROCESSABLE,
    }

    private final String code;
    private final Kind kind;
    private final Map<String, Object> details;

    public ModelAuthoringException(String code, String message, Kind kind) {
        this(code, message, kind, Map.of());
    }

    public ModelAuthoringException(String code, String message, Kind kind, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.kind = kind;
        this.details = Map.copyOf(details == null ? Map.of() : details);
    }

    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }

    public Map<String, Object> details() {
        return details;
    }
}
