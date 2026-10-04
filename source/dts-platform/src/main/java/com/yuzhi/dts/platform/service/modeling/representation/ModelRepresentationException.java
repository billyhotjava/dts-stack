package com.yuzhi.dts.platform.service.modeling.representation;

import java.util.Map;

public class ModelRepresentationException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
    }

    private final String code;
    private final Kind kind;
    private final Map<String, Object> details;

    public ModelRepresentationException(String code, String message, Kind kind) {
        this(code, message, kind, Map.of());
    }

    public ModelRepresentationException(String code, String message, Kind kind, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.kind = kind;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String getCode() {
        return code;
    }

    public Kind getKind() {
        return kind;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
