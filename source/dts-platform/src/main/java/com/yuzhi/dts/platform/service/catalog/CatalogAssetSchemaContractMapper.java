package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.util.StringUtils;

public final class CatalogAssetSchemaContractMapper {

    private CatalogAssetSchemaContractMapper() {}

    public static CatalogAssetSchemaContract fromOpenMetadata(CatalogAssetContract asset, List<OpenMetadataColumnCache> columns) {
        List<CatalogAssetColumnContract> mapped = safeList(columns)
            .stream()
            .sorted(Comparator.comparing(OpenMetadataColumnCache::getOrdinalPosition, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(column -> text(column.getName()), String.CASE_INSENSITIVE_ORDER))
            .map(CatalogAssetSchemaContractMapper::fromOpenMetadataColumn)
            .toList();
        return new CatalogAssetSchemaContract(asset, mapped, mapped.size(), "openmetadata-cache");
    }

    public static CatalogAssetSchemaContract fromLegacy(CatalogAssetContract asset, List<CatalogColumnSchema> columns) {
        AtomicInteger ordinal = new AtomicInteger(1);
        List<CatalogAssetColumnContract> mapped = safeList(columns)
            .stream()
            .sorted(Comparator.comparing(column -> text(column.getName()), String.CASE_INSENSITIVE_ORDER))
            .map(column -> fromLegacyColumn(column, ordinal.getAndIncrement()))
            .toList();
        return new CatalogAssetSchemaContract(asset, mapped, mapped.size(), "dts-catalog");
    }

    private static CatalogAssetColumnContract fromOpenMetadataColumn(OpenMetadataColumnCache column) {
        return new CatalogAssetColumnContract(
            column.getId(),
            column.getName(),
            column.getDataType(),
            null,
            column.getOrdinalPosition(),
            column.getDescription(),
            column.getTagsJson(),
            null,
            null,
            null,
            null,
            "openmetadata-cache",
            column.getLastModifiedDate()
        );
    }

    private static CatalogAssetColumnContract fromLegacyColumn(CatalogColumnSchema column, int ordinal) {
        return new CatalogAssetColumnContract(
            column.getId(),
            column.getName(),
            column.getDataType(),
            column.getNullable(),
            ordinal,
            column.getComment(),
            column.getTags(),
            column.getSensitiveTags(),
            column.getStandardId(),
            column.getStandardRule(),
            column.getStatus(),
            "dts-catalog",
            column.getLastModifiedDate()
        );
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
