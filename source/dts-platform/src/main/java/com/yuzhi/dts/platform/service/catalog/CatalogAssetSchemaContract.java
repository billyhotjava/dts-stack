package com.yuzhi.dts.platform.service.catalog;

import java.util.List;

public record CatalogAssetSchemaContract(
    CatalogAssetContract asset,
    List<CatalogAssetColumnContract> columns,
    int columnCount,
    String schemaSource
) {
    public CatalogAssetSchemaContract {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
