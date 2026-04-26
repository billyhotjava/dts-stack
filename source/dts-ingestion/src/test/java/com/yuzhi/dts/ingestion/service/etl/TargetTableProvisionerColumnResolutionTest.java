package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TargetTableProvisionerColumnResolutionTest {

    @Test
    void shouldPreserveJdbcTypesWhenExplicitColumnsAreSelected() {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        IngestionSchemaSnapshotService snapshotService = mock(IngestionSchemaSnapshotService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            new ObjectMapper(),
            snapshotService
        );
        JdbcMetadataService.JdbcConnectionInfo sourceInfo = new JdbcMetadataService.JdbcConnectionInfo(
            "jdbc:postgresql://127.0.0.1:5432/source",
            "user",
            "pwd",
            "org.postgresql.Driver",
            null,
            Map.of()
        );
        when(metadataService.getTableColumns(sourceInfo, "public.orders")).thenReturn(List.of(
            new JdbcMetadataService.ColumnMeta("id", Types.BIGINT, "BIGINT", null, null),
            new JdbcMetadataService.ColumnMeta("amount", Types.NUMERIC, "NUMERIC", 18, 2),
            new JdbcMetadataService.ColumnMeta("created_at", Types.TIMESTAMP, "TIMESTAMP", null, null)
        ));

        List<JdbcMetadataService.ColumnMeta> columns = provisioner.resolveColumns(
            sourceInfo,
            "public.orders",
            Map.of("column", List.of("amount", "created_at"))
        );

        assertThat(columns).extracting(JdbcMetadataService.ColumnMeta::name)
            .containsExactly("amount", "created_at");
        assertThat(columns.get(0).jdbcType()).isEqualTo(Types.NUMERIC);
        assertThat(columns.get(0).columnSize()).isEqualTo(18);
        assertThat(columns.get(0).decimalDigits()).isEqualTo(2);
        assertThat(columns.get(1).jdbcType()).isEqualTo(Types.TIMESTAMP);
    }

    @Test
    void shouldFallbackToTextWhenExplicitColumnMetadataIsUnavailable() {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            new ObjectMapper(),
            mock(IngestionSchemaSnapshotService.class)
        );
        JdbcMetadataService.JdbcConnectionInfo sourceInfo = new JdbcMetadataService.JdbcConnectionInfo(
            null,
            null,
            null,
            null,
            null,
            Map.of()
        );

        List<JdbcMetadataService.ColumnMeta> columns = provisioner.resolveColumns(
            sourceInfo,
            "orders",
            Map.of("column", List.of("amount"))
        );

        assertThat(columns).hasSize(1);
        assertThat(columns.get(0).name()).isEqualTo("amount");
        assertThat(columns.get(0).jdbcType()).isEqualTo(Types.VARCHAR);
        assertThat(columns.get(0).typeName()).isEqualTo("TEXT");
    }
}
