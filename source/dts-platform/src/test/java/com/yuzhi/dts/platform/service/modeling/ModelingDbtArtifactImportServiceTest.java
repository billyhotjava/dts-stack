package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ArtifactType;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportCommand;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportResult;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.ImportedArtifact;
import com.yuzhi.dts.platform.service.modeling.ModelingDbtArtifactImportService.NodeKind;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelingDbtArtifactImportServiceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000071");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000071");
    private static final UUID IMPLEMENTATION_ID = UUID.fromString("60000000-0000-0000-0000-000000000071");
    private static final String MODEL_CHECKSUM = "a".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "b".repeat(64);

    @Test
    void storesModelAndMultipleTechnicalNodesAsImportedAndReplaysIdenticalContent() {
        JdbcTemplate jdbcTemplate = pinnedJdbcTemplate();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(
            jdbcTemplate.queryForObject(
                contains("select count(*)"),
                eq(Integer.class),
                any(Object[].class)
            )
        ).thenReturn(1);
        ModelingDbtArtifactImportService service = new ModelingDbtArtifactImportService(jdbcTemplate);
        ImportCommand command = command(allArtifacts());

        ImportResult result = service.importArtifacts(command);

        assertThat(result)
            .extracting(
                ImportResult::modelSpecId,
                ImportResult::modelRevision,
                ImportResult::implementationRevision,
                ImportResult::implementationChecksum,
                ImportResult::artifactCount
            )
            .containsExactly(MODEL_ID, 7, 3, IMPLEMENTATION_CHECKSUM, 12);
        verify(jdbcTemplate, times(3)).query(
            contains("pg_advisory_xact_lock"),
            any(org.springframework.jdbc.core.ResultSetExtractor.class),
            any(Object[].class)
        );
        verify(jdbcTemplate, times(3)).queryForObject(
            contains("select exists"),
            eq(Boolean.class),
            any(Object[].class)
        );
        verify(jdbcTemplate, times(12)).update(
            contains("'IMPORTED'"),
            any(Object[].class)
        );
        verify(jdbcTemplate, times(12)).queryForObject(
            contains("a.project_key = ? and a.dbt_unique_id = ?"),
            eq(Integer.class),
            any(Object[].class)
        );
    }

    @Test
    void failsClosedWhenTheSameNodeTypeSlotContainsDifferentContent() {
        JdbcTemplate jdbcTemplate = pinnedJdbcTemplate();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(
            jdbcTemplate.queryForObject(
                contains("select count(*)"),
                eq(Integer.class),
                any(Object[].class)
            )
        ).thenReturn(0);
        ModelingDbtArtifactImportService service = new ModelingDbtArtifactImportService(jdbcTemplate);

        assertThatThrownBy(() ->
            service.importArtifacts(command(List.of(artifact(
                "model.pjm.budget",
                NodeKind.MODEL,
                ArtifactType.SQL,
                "models/dwd/budget.sql",
                "select changed_budget_id from raw_budget",
                "table"
            ))))
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_ARTIFACT_REVISION_CONFLICT");
    }

    @Test
    void rejectsATechnicalNodeAlreadyOwnedByAnotherModel() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.queryForList(
                contains("jsonb_array_length(i.inputs_json) = 1"),
                eq(UUID.class),
                any(Object[].class)
            )
        ).thenReturn(List.of(IMPLEMENTATION_ID));
        when(
            jdbcTemplate.queryForObject(
                contains("select exists"),
                eq(Boolean.class),
                any(Object[].class)
            )
        ).thenAnswer(invocation -> {
            for (Object argument : invocation.getArguments()) {
                if ("model.pjm.stg_budget".equals(argument)) {
                    return true;
                }
                if (
                    argument instanceof Object[] nestedArguments &&
                    java.util.Arrays.asList(nestedArguments).contains("model.pjm.stg_budget")
                ) {
                    return true;
                }
            }
            return false;
        });
        ModelingDbtArtifactImportService service = new ModelingDbtArtifactImportService(jdbcTemplate);

        assertThatThrownBy(() ->
            service.importArtifacts(command(List.of(
                artifact(
                    "model.pjm.budget",
                    NodeKind.MODEL,
                    ArtifactType.SQL,
                    "models/dwd/budget.sql",
                    "select * from model.pjm.stg_budget",
                    "table"
                ),
                artifact(
                    "model.pjm.stg_budget",
                    NodeKind.STG,
                    ArtifactType.SQL,
                    "models/staging/stg_budget.sql",
                    "select * from raw_budget",
                    "view"
                )
            )))
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPLEMENTATION_DBT_CONFLICT");
    }

    @Test
    void validatesChecksumAndUniqueNodeTypeSlotsBeforeAnyWrite() {
        assertThatThrownBy(() ->
            new ImportedArtifact(
                "model.pjm.budget",
                NodeKind.MODEL,
                ArtifactType.SQL,
                "models/dwd/budget.sql",
                "0".repeat(64),
                "select 1",
                "table"
            )
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("checksum");

        ImportedArtifact sql = artifact(
            "model.pjm.budget",
            NodeKind.MODEL,
            ArtifactType.SQL,
            "models/dwd/budget.sql",
            "select 1",
            "table"
        );
        assertThatThrownBy(() -> command(List.of(sql, sql)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("slots");
    }

    private static JdbcTemplate pinnedJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(
            jdbcTemplate.queryForList(
                contains("jsonb_array_length(i.inputs_json) = 1"),
                eq(UUID.class),
                any(Object[].class)
            )
        ).thenReturn(List.of(IMPLEMENTATION_ID));
        when(
            jdbcTemplate.queryForObject(
                contains("select exists"),
                eq(Boolean.class),
                any(Object[].class)
            )
        ).thenReturn(false);
        return jdbcTemplate;
    }

    private static ImportCommand command(List<ImportedArtifact> artifacts) {
        return new ImportCommand(
            "tenant-a",
            MODEL_ID,
            PLAN_ID,
            7,
            MODEL_CHECKSUM,
            ModelStatus.DRAFT,
            IMPLEMENTATION_ID,
            3,
            IMPLEMENTATION_CHECKSUM,
            "pjm",
            "model.pjm.budget",
            "apply-run:model.pjm.budget:artifacts",
            artifacts
        );
    }

    private static List<ImportedArtifact> allArtifacts() {
        List<ImportedArtifact> result = new ArrayList<>();
        for (ArtifactType type : ArtifactType.values()) {
            result.add(artifact(
                "model.pjm.budget",
                NodeKind.MODEL,
                type,
                "models/dwd/budget.sql",
                content(type, "budget"),
                "table"
            ));
            result.add(artifact(
                "model.pjm.stg_budget",
                NodeKind.STG,
                type,
                "models/staging/stg_budget.sql",
                content(type, "stg_budget"),
                "view"
            ));
            result.add(artifact(
                "model.pjm.ephemeral_budget",
                NodeKind.EPHEMERAL,
                type,
                "models/staging/ephemeral_budget.sql",
                content(type, "ephemeral_budget"),
                "ephemeral"
            ));
        }
        return List.copyOf(result);
    }

    private static String content(ArtifactType type, String node) {
        return switch (type) {
            case SQL -> "select * from " + node;
            case SCHEMA -> "{\"columns\":[],\"node\":\"" + node + "\"}";
            case CONFIG -> "{\"materialized\":\"" + node + "\"}";
            case DEPENDENCY -> "[\"" + node + "\"]";
        };
    }

    private static ImportedArtifact artifact(
        String dbtUniqueId,
        NodeKind nodeKind,
        ArtifactType type,
        String path,
        String content,
        String materialization
    ) {
        return new ImportedArtifact(
            dbtUniqueId,
            nodeKind,
            type,
            path,
            ModelPackageChecksum.sha256Text(content),
            content,
            materialization
        );
    }
}
