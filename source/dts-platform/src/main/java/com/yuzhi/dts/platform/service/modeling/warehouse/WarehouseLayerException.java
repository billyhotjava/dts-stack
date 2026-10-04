package com.yuzhi.dts.platform.service.modeling.warehouse;

/** Stable application error exposed by the warehouse-layer governance boundary. */
public class WarehouseLayerException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        UNPROCESSABLE,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
    }

    private final String code;
    private final Kind kind;
    private final Object details;

    public WarehouseLayerException(String code, String message, Kind kind) {
        this(code, message, kind, null);
    }

    public WarehouseLayerException(String code, String message, Kind kind, Object details) {
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
