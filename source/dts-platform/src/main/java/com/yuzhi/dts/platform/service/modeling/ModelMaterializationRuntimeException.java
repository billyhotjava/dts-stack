package com.yuzhi.dts.platform.service.modeling;

/** Stable internal runtime-spec failure without token, path or credential details. */
public class ModelMaterializationRuntimeException
    extends RuntimeException {

    private final String code;

    public ModelMaterializationRuntimeException(
        String code,
        String message
    ) {
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
