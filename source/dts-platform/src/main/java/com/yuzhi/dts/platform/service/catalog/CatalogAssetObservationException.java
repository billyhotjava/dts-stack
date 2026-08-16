package com.yuzhi.dts.platform.service.catalog;

/** Fail-closed adapter error raised before a producer may claim an asset observation succeeded. */
public final class CatalogAssetObservationException extends RuntimeException {

    private final String code;

    public CatalogAssetObservationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
