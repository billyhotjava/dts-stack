package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelLifecycleRepositoryTest {

    @Test
    void persistsImplementationBoundEphemeralStgWithoutPhysicalAssetAndNeverUpdatesDbtManagedRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        ModelLifecycleRepository repository = new ModelLifecycleRepository(jdbcTemplate, new ObjectMapper());
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(modelId);
        when(model.planId()).thenReturn(planId);
        when(model.revision()).thenReturn(2);
        when(model.checksum()).thenReturn("a".repeat(64));
        ImplementationView implementation = new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            modelId,
            planId,
            2,
            "a".repeat(64),
            ImplementationMode.DESIGNER_GENERATED,
            "warehouse",
            "model.customer_detail",
            "ACTIVE",
            6,
            "b".repeat(64),
            InputMode.GENERATED,
            List.of(new com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(),
            "table"
        );

        repository.saveArtifacts(
            "tenant-a",
            model,
            implementation,
            "compile-2",
            List.of(new ArtifactWrite("STG_SQL", "models/dwd/customer_detail/v2/i6/stg_customer_detail.sql", "c".repeat(64), "select 1", "STG", "ephemeral", null)),
            Instant.parse("2026-07-24T00:00:00Z")
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue())
            .contains("implementation_revision, node_kind, materialization")
            .contains("on conflict (model_spec_id, revision, implementation_revision, artifact_key)")
            .contains("modeling_dbt_artifact.ownership <> 'DBT_MANAGED'");
        assertThat(arguments.getValue()).containsSequence(6, "STG", "ephemeral", null);
    }

    @Test
    void usesTheStableDbtArtifactSlotAndImplementationRevisionAsImmutableIdentity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        ModelLifecycleRepository repository = new ModelLifecycleRepository(jdbcTemplate, new ObjectMapper());
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(modelId);
        when(model.planId()).thenReturn(planId);
        when(model.revision()).thenReturn(2);
        when(model.checksum()).thenReturn("a".repeat(64));
        ImplementationView implementation = new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            modelId,
            planId,
            2,
            "a".repeat(64),
            ImplementationMode.DBT_MANAGED,
            "warehouse",
            "model.customer_detail",
            "ACTIVE",
            7,
            "b".repeat(64),
            InputMode.UPSTREAM_MODEL,
            List.of(),
            List.of(),
            Map.of(),
            "table"
        );

        repository.saveDbtManagedArtifacts(
            "tenant-a",
            model,
            implementation,
            "dbt-manifest-7",
            List.of(
                new ArtifactWrite(
                    "SQL",
                    "models/dwd/customer_detail.sql",
                    "c".repeat(64),
                    "select 1",
                    "MODEL",
                    "table",
                    null
                )
            ),
            Instant.parse("2026-07-24T00:00:00Z")
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
            .contains("model_spec_id, revision, implementation_revision,")
            .contains("project_key, dbt_unique_id, node_kind, artifact_type")
            .contains("where ownership = 'DBT_MANAGED'")
            .contains("modeling_dbt_artifact.path = excluded.path")
            .doesNotContain("set implementation_revision = excluded.implementation_revision");
    }
}
