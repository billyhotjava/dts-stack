package com.yuzhi.dts.platform.service.catalog;

/** Stable rejection raised when one asset identity resolves to conflicting durable resources. */
public final class CatalogAssetSemanticConflictException extends RuntimeException {

    private final String code;

    public CatalogAssetSemanticConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
