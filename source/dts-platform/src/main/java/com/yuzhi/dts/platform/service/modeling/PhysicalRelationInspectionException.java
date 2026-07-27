package com.yuzhi.dts.platform.service.modeling;

/** Stable fail-closed relation-inspection failure without JDBC or credential details. */
public class PhysicalRelationInspectionException
    extends RuntimeException {

    private final String code;

    public PhysicalRelationInspectionException(
        String code,
        String message
    ) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        this.code = code.trim();
    }

    public PhysicalRelationInspectionException(
        String code,
        String message,
        Throwable cause
    ) {
        super(message, cause);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        this.code = code.trim();
    }

    public String code() {
        return code;
    }
}
