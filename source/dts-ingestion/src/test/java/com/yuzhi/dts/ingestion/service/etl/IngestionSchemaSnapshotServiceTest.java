package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionSchemaSnapshot;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionSchemaSnapshotRepository;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class IngestionSchemaSnapshotServiceTest {

    @Mock
    private IngestionSchemaSnapshotRepository repository;

    private IngestionSchemaSnapshotService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new IngestionSchemaSnapshotService(repository, new ObjectMapper());
        when(repository.save(any(IngestionSchemaSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldSaveSourceAndTechnicalColumnsForOdsSnapshot() {
        IngestionTask task = new IngestionTask();
        task.setId(10L);
        task.setSourceType("rdbmsreader");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(99L);

        List<JdbcMetadataService.ColumnMeta> sourceColumns = List.of(
            new JdbcMetadataService.ColumnMeta("ID", Types.BIGINT, "BIGINT", null, null, false, null, "primary id", 1),
            new JdbcMetadataService.ColumnMeta("AMOUNT", Types.DECIMAL, "DECIMAL", 18, 2, true, "0", "amount", 2)
        );
        List<JdbcMetadataService.ColumnMeta> odsColumns = List.of(
            sourceColumns.get(0).withName("id"),
            sourceColumns.get(1).withName("amount"),
            new JdbcMetadataService.ColumnMeta("_dts_batch_id", Types.VARCHAR, "VARCHAR", 128, null)
        );

        IngestionSchemaSnapshot snapshot = service.saveSnapshot(
            task,
            execution,
            "rdbmsreader",
            "ERP",
            "ERPDEMO",
            "CUSTOMER",
            "ERPDEMO.CUSTOMER",
            "ods",
            "ods_customer",
            sourceColumns,
            odsColumns,
            List.of("ID"),
            List.of(new JdbcMetadataService.IndexMeta("idx_customer_amount", false, List.of("AMOUNT"))),
            List.of()
        );

        assertThat(snapshot.getSchemaFingerprint()).hasSize(64);
        assertThat(snapshot.getColumnsJson()).hasSize(3);
        assertThat(snapshot.getColumnsJson().get(0).get("sourceName").asText()).isEqualTo("ID");
        assertThat(snapshot.getColumnsJson().get(0).get("odsName").asText()).isEqualTo("id");
        assertThat(snapshot.getColumnsJson().get(0).get("nullable").asBoolean()).isFalse();
        assertThat(snapshot.getColumnsJson().get(1).get("targetDecimalDigits").asInt()).isEqualTo(2);
        assertThat(snapshot.getColumnsJson().get(2).get("technical").asBoolean()).isTrue();
        assertThat(snapshot.getPrimaryKeyColumns().get(0).asText()).isEqualTo("ID");
        assertThat(snapshot.getIndexesJson().get(0).get("name").asText()).isEqualTo("idx_customer_amount");

        List<JdbcMetadataService.ColumnMeta> restored = service.toOdsColumns(snapshot);
        assertThat(restored).extracting(JdbcMetadataService.ColumnMeta::name)
            .containsExactly("id", "amount", "_dts_batch_id");
        assertThat(restored.get(1).decimalDigits()).isEqualTo(2);
    }
}
