package com.yuzhi.dts.platform.service.catalog;

public class CatalogClassificationException extends RuntimeException {

    private final String code;

    public CatalogClassificationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
