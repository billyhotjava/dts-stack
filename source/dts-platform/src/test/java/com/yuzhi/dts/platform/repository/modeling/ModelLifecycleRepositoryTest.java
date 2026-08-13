package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelLifecycleRepositoryTest {

    @Test
    void designerToDbtTransitionLocksTheSourcePinsAndWritesTheTargetPins() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        ModelLifecycleRepository repository = new ModelLifecycleRepository(jdbcTemplate, new ObjectMapper());
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000091");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000091");
        ModelSpecView target = mock(ModelSpecView.class);
        when(target.id()).thenReturn(modelId);
        when(target.planId()).thenReturn(planId);
        when(target.revision()).thenReturn(8);
        when(target.checksum()).thenReturn("c".repeat(64));
        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("projectKey", "dts", "dbtUniqueId", "model.dts.model_91"))),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "table",
            "transition-91"
        );

        assertThat(repository.transitionDesignerImplementationToDbtManaged(
            "tenant-a", "alice", target, "dts", "model.dts.model_91", command,
            7, "a".repeat(64), 3, "b".repeat(64), Instant.parse("2026-08-13T00:00:00Z")
        )).isEqualTo(1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Integer.class), arguments.capture());
        assertThat(sql.getValue())
            .contains("model_revision = ? and model_checksum = ?")
            .contains("ownership = 'DESIGNER_GENERATED'")
            .contains("implementation_revision = ?")
            .contains("ownership='DBT_MANAGED'")
            .contains("insert into modeling_model_implementation_revision");
        assertThat(sql.getValue().chars().filter(character -> character == '?').count())
            .isEqualTo((long) arguments.getValue().length);
        assertThat(arguments.getValue()).containsSequence(
            "tenant-a", modelId, 7, "a".repeat(64), 3, "b".repeat(64),
            planId, 8, "c".repeat(64), "dts", "model.dts.model_91"
        );
    }

    @Test
    void advancesImportedDbtImplementationFromAPriorLogicalRevisionUsingTheImplementationCasPins() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000084");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000084");
        UUID implementationId = UUID.fromString("60000000-0000-0000-0000-000000000084");
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(Object[].class)))
            .thenReturn(List.of(modelId), List.of(implementationId));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        ModelLifecycleRepository repository = new ModelLifecycleRepository(jdbcTemplate, new ObjectMapper());
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(modelId);
        when(model.planId()).thenReturn(planId);
        when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn("c".repeat(64));
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(model.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("projectKey", "pjm", "dbtUniqueId", "model.pjm.progress"))),
            List.of(),
            Map.of("targetPhysicalName", "progress", "loadStrategy", "FULL", "partitionFields", List.of()),
            ImplementationMode.DBT_MANAGED,
            "table",
            "advance-84"
        );

        repository.saveImportedDbtImplementation(
            "tenant-a",
            "alice",
            model,
            ModelStatus.DRAFT,
            "pjm",
            "model.pjm.progress",
            command,
            1,
            "b".repeat(64),
            Instant.parse("2026-08-12T00:00:00Z")
        );

        ArgumentCaptor<String> lockSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(lockSql.capture(), eq(UUID.class), any(Object[].class));
        String implementationLock = lockSql.getAllValues().get(1);
        assertThat(implementationLock)
            .contains("implementation_revision = ?")
            .contains("current_implementation_checksum = ?")
            .doesNotContain("model_revision = ?")
            .doesNotContain("model_checksum = ?");
    }

    @Test
    void currentArtifactTypesIncludesImportedAndCompiledEvidenceForThePinnedImplementation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
            .thenReturn(List.of("SQL", "SCHEMA", "CONFIG"));
        ModelLifecycleRepository repository = new ModelLifecycleRepository(jdbcTemplate, new ObjectMapper());
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000083");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000083");
        ImplementationView implementation = new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000083"),
            modelId,
            planId,
            2,
            "a".repeat(64),
            ImplementationMode.DBT_MANAGED,
            "pjm",
            "model.pjm.dim_completion_status_v2",
            "ACTIVE",
            1,
            "b".repeat(64),
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of())),
            List.of(),
            Map.of(),
            "table"
        );

        assertThat(repository.currentArtifactTypes("tenant-a", modelId, implementation))
            .containsExactlyInAnyOrder("SQL", "SCHEMA", "CONFIG");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(String.class), any(Object[].class));
        assertThat(sql.getValue()).contains("a.status in ('IMPORTED', 'COMPILED')");
    }

    @Test
    void promotesOnlyImportedArtifactsPinnedToTheCurrentDbtImplementation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(3);
        ModelLifecycleRepository repository = new ModelLifecycleRepository(jdbcTemplate, new ObjectMapper());
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000083");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000083");
        ImplementationView implementation = new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000083"),
            modelId,
            planId,
            2,
            "a".repeat(64),
            ImplementationMode.DBT_MANAGED,
            "pjm",
            "model.pjm.dim_completion_status_v2",
            "ACTIVE",
            1,
            "b".repeat(64),
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of())),
            List.of(),
            Map.of(),
            "table"
        );
        Instant now = Instant.parse("2026-08-12T00:00:00Z");

        assertThat(repository.promoteImportedArtifactsToCompiled("tenant-a", modelId, implementation, now)).isEqualTo(3);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue())
            .contains("set status = 'COMPILED'")
            .contains("last_modified_date = ?")
            .contains("a.status = 'IMPORTED'")
            .contains("ir.content_checksum = ?");
        assertThat(sql.getValue().chars().filter(character -> character == '?').count())
            .isEqualTo((long) arguments.getValue().length);
    }

    @Test
    void restoresImportedDbtHeadAgainstOldPinsAndAppendsANewRevision() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        ModelLifecycleRepository repository = new ModelLifecycleRepository(jdbcTemplate, new ObjectMapper());
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000083");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000083");
        ModelSpecView restored = mock(ModelSpecView.class);
        when(restored.id()).thenReturn(modelId);
        when(restored.planId()).thenReturn(planId);
        when(restored.revision()).thenReturn(12);
        when(restored.checksum()).thenReturn("c".repeat(64));
        when(restored.status()).thenReturn(ModelStatus.DRAFT);
        when(restored.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("projectKey", "finance", "dbtUniqueId", "model.finance.budget"))),
            List.of(),
            Map.of(
                "targetPhysicalName",
                "budget",
                "loadStrategy",
                "FULL",
                "partitionFields",
                List.of()
            ),
            ImplementationMode.DBT_MANAGED,
            "table",
            "undo-83"
        );
        String restoredImplementationChecksum = new ModelImplementationChecksumCodec(new ObjectMapper())
            .contentChecksum(command);

        int changed = repository.restoreImportedDbtImplementation(
            "tenant-a",
            "alice",
            restored,
            "finance",
            "model.finance.budget",
            command,
            11,
            "a".repeat(64),
            6,
            "b".repeat(64),
            restoredImplementationChecksum,
            Instant.parse("2026-08-02T00:00:00Z")
        );

        assertThat(changed).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Integer.class), arguments.capture());
        assertThat(sql.getValue())
            .contains("from modeling_model_spec")
            .contains("spec.revision = ? and spec.current_checksum = ? and spec.status = 'DRAFT'")
            .contains("implementation.model_revision = ? and implementation.model_checksum = ?")
            .contains("implementation.implementation_revision = ?")
            .contains("implementation.current_implementation_checksum = ?")
            .contains("implementation_revision = locked_head.implementation_revision + 1")
            .contains("insert into modeling_model_implementation_revision");
        assertThat(sql.getValue().chars().filter(character -> character == '?').count())
            .isEqualTo((long) arguments.getValue().length);
    }

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
        assertThat(sql.getValue().chars().filter(character -> character == '?').count())
            .isEqualTo((long) arguments.getValue().length);
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
