package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogAssetSchemaContractMapperTest {

    @Test
    void openMetadataColumnsAreOrderedAndMappedToStableContract() {
        CatalogAssetContract asset = assetContract();
        OpenMetadataColumnCache second = new OpenMetadataColumnCache();
        second.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        second.setName("amount");
        second.setDataType("decimal");
        second.setOrdinalPosition(2);
        second.setDescription("Order amount");

        OpenMetadataColumnCache first = new OpenMetadataColumnCache();
        first.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        first.setName("order_id");
        first.setDataType("varchar");
        first.setOrdinalPosition(1);

        CatalogAssetSchemaContract contract = CatalogAssetSchemaContractMapper.fromOpenMetadata(asset, List.of(second, first));

        assertThat(contract.schemaSource()).isEqualTo("openmetadata-cache");
        assertThat(contract.columns()).extracting(CatalogAssetColumnContract::name).containsExactly("order_id", "amount");
        assertThat(contract.columns().get(0).nullable()).isNull();
    }

    @Test
    void legacyColumnsExposeNullableStandardAndSensitiveTags() {
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        column.setName("customer_phone");
        column.setDataType("varchar");
        column.setNullable(false);
        column.setSensitiveTags("PII:phone");
        column.setStandardId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        column.setStatus("ACTIVE");

        CatalogAssetSchemaContract contract = CatalogAssetSchemaContractMapper.fromLegacy(assetContract(), List.of(column));

        assertThat(contract.schemaSource()).isEqualTo("dts-catalog");
        assertThat(contract.columns()).hasSize(1);
        assertThat(contract.columns().get(0).nullable()).isFalse();
        assertThat(contract.columns().get(0).sensitiveTags()).isEqualTo("PII:phone");
        assertThat(contract.columns().get(0).standardId()).isEqualTo(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    }

    private CatalogAssetContract assetContract() {
        return new CatalogAssetContract(
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            "DATASET",
            "source:unknown/schema:dwd/table:orders",
            "DATASET",
            "99999999-9999-9999-9999-999999999999",
            "dts-catalog:99999999-9999-9999-9999-999999999999",
            null,
            "dwd.orders",
            "Orders",
            null,
            "dwd",
            "dwd",
            "orders",
            "INTERNAL",
            "DWD",
            "D01",
            "owner",
            null,
            "ACTIVE",
            "GOVERNED",
            List.of(),
            true,
            "DTS_NATIVE",
            null,
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            "SYNCED",
            "dts-catalog"
        );
    }
}
