package com.yuzhi.dts.platform.service.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CatalogAssetTagPermissionException extends ResponseStatusException {

    private final String reasonCode;
    private final CatalogAssetType assetType;
    private final String assetKey;
    private final int deniedCount;

    public CatalogAssetTagPermissionException(
        HttpStatus status,
        String reasonCode,
        CatalogAssetType assetType,
        String assetKey,
        String message
    ) {
        this(
            status,
            reasonCode,
            assetType,
            assetKey,
            message,
            status == HttpStatus.FORBIDDEN ? 1 : 0
        );
    }

    public CatalogAssetTagPermissionException(
        HttpStatus status,
        String reasonCode,
        CatalogAssetType assetType,
        String assetKey,
        String message,
        int deniedCount
    ) {
        super(status, reasonCode + ": " + message);
        this.reasonCode = reasonCode;
        this.assetType = assetType;
        this.assetKey = assetKey;
        this.deniedCount = Math.max(0, deniedCount);
    }

    public String reasonCode() {
        return reasonCode;
    }

    public CatalogAssetType assetType() {
        return assetType;
    }

    public String assetKey() {
        return assetKey;
    }

    public int deniedCount() {
        return deniedCount;
    }
}
