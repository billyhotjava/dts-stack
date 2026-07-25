package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL-backed acceptance of the modeling ledger, dbt artifact and run boundary. */
@IntegrationTest
class ModelingVNextApplicationServiceIT {

    @Autowired
    private ModelingVNextApplicationService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void legacyPlanRouteWritesOneCanonicalPlanAndReturnsItsPlanId() {
        String tenant = "it-plan-" + UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        ModelingVNextApplicationService.WarehousePlan request = new ModelingVNextApplicationService.WarehousePlan(
            null,
            null,
            domainId.toString(),
            "risk-resolution",
            "DWD",
            "DESIGNER_GENERATED",
            "one row per risk lifecycle",
            "DRAFT",
            1
        );

        try {
            ModelingVNextApplicationService.WarehousePlan saved = service.savePlan(tenant, request, 1, "legacy-plan-1");

            assertThat(saved.planId()).isEqualTo(saved.id()).isNotBlank();
            assertThat(service.listPlans(tenant, "risk-resolution", "DWD"))
                .singleElement()
                .satisfies(plan -> {
                    assertThat(plan.planId()).isEqualTo(saved.planId());
                    assertThat(plan.targetGrain()).isEqualTo("one row per risk lifecycle");
                });
            assertThat(
                jdbcTemplate.queryForMap(
                    "select code, name, onboarding_mode, lifecycle_status from modeling_warehouse_plan where tenant_id = ? and id = ?",
                    tenant,
                    UUID.fromString(saved.planId())
                )
            )
                .containsEntry("onboarding_mode", "BUSINESS_FIRST")
                .containsEntry("lifecycle_status", "DRAFT");
        } finally {
            jdbcTemplate.update("delete from modeling_warehouse_plan where tenant_id = ?", tenant);
        }
    }

    @Test
    void pjmGoldenPathRejectsGeneratedCompileImportsPinnedDbtArtifactsAndQueuesRun() {
        String tenant = "it-" + UUID.randomUUID();
        UUID seed = UUID.randomUUID();
        String objectKey = "pjm-project-node-it-" + seed.toString().substring(0, 8);
        String modelKey = "pjm-project-node-dwd-it-" + seed.toString().substring(0, 8);
        ModelingVNextContract.BusinessObject object = new ModelingVNextContract.BusinessObject(
            objectKey, "project_node_" + seed.toString().substring(0, 8), "项目节点", "PJM 项目节点事实对象",
            ModelingVNextContract.ObjectKind.FACT, "project-node-plan-loop", List.of("project_no", "plan_date"),
            new ModelingVNextContract.Grain("一个项目在一个计划日的一条节点记录", List.of("project_no", "plan_date")),
            List.of(new ModelingVNextContract.SourceRef("TABLE", "ods_project_node", ModelingVNextContract.Layer.ODS)),
            "DRAFT", ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED
        );
        ModelingVNextContract.ModelSpec model = new ModelingVNextContract.ModelSpec(
            modelKey, objectKey, object.processId(), ModelingVNextContract.Layer.DWD,
            ModelingVNextContract.ModelType.FACT, ModelingVNextContract.ImplementationMode.DBT_MANAGED,
            "project_node_detail_" + seed.toString().substring(0, 8), object.grain(),
            List.of(new ModelingVNextContract.StandardBinding("project_no", "std.project.code", null, null)),
            object.sourceRefs(), List.of(), List.of("node_count"), "table", 1
        );

        try {
            ModelingVNextContract.BusinessObject savedObject = service.saveBusinessObject(tenant, object, 1, "bo-" + objectKey);
            assertThat(service.listBusinessObjects(tenant, object.processId(), "DRAFT")).extracting(ModelingVNextContract.BusinessObject::id).contains(savedObject.id());

            ModelingVNextContract.ModelSpec savedModel = service.saveModelSpec(tenant, model, 1, "model-" + modelKey);
            ModelingVNextApplicationService.CompileResult refusedCompile = service.compile(
                tenant,
                savedModel.id(),
                1,
                "compile-" + modelKey
            );
            assertThat(refusedCompile.status()).isEqualTo("FAILED");
            assertThat(refusedCompile.issues()).containsExactly("DBT_MANAGED_ARTIFACT_REQUIRED");
            assertThat(refusedCompile.artifacts()).isEmpty();

            UUID implementationId = UUID.randomUUID();
            UUID implementationPlanId = UUID.randomUUID();
            String modelChecksum = jdbcTemplate.queryForObject(
                "select content_checksum from modeling_model_spec_revision where model_spec_id = ? and revision = ?",
                String.class,
                UUID.fromString(savedModel.id()),
                savedModel.revision()
            );
            String implementationChecksum = "b".repeat(64);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.update(
                    """
                    insert into modeling_model_implementation (
                        id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum, ownership,
                        project_key, dbt_unique_id, status, idempotency_key, created_by, created_date,
                        last_modified_date, implementation_revision, current_implementation_checksum,
                        input_mode, inputs_json, field_mappings_json, settings_json, materialization
                    ) values (?, ?, ?, ?, ?, ?, 'DBT_MANAGED', 'legacy-pjm', 'model.legacy.project_node',
                              'ACTIVE', ?, 'integration-test', now(), now(), 1, ?, 'GENERATED',
                              cast('[{"generatorType":"SQL_WORKSPACE","config":{}}]' as jsonb),
                              cast('[]' as jsonb), cast('{}' as jsonb), 'table')
                    """,
                    implementationId,
                    tenant,
                    UUID.fromString(savedModel.id()),
                    implementationPlanId,
                    savedModel.revision(),
                    modelChecksum,
                    "implementation-" + modelKey,
                    implementationChecksum
                );
                jdbcTemplate.update(
                    """
                    insert into modeling_model_implementation_revision (
                        id, tenant_id, implementation_id, revision, content_checksum, input_mode,
                        inputs_json, field_mappings_json, settings_json, ownership, materialization,
                        created_by, created_date
                    ) values (?, ?, ?, 1, ?, 'GENERATED',
                              cast('[{"generatorType":"SQL_WORKSPACE","config":{}}]' as jsonb),
                              cast('[]' as jsonb), cast('{}' as jsonb), 'DBT_MANAGED', 'table',
                              'integration-test', now())
                    """,
                    UUID.randomUUID(),
                    tenant,
                    implementationId,
                    implementationChecksum
                );
            });

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("resource_type", "model");
            node.put("name", "legacy_project_node");
            node.put("raw_code", "select project_no from ods_project_node");
            node.put("columns", Map.of("project_no", Map.of("name", "project_no")));
            node.put("depends_on", Map.of("nodes", List.of("source.project.ods_project_node")));
            Map<String, Object> manifest = Map.of("nodes", Map.of("model.legacy.project_node", node));
            UUID artifactPlanId = jdbcTemplate.queryForObject(
                "select plan_id from modeling_model_spec where id = ?",
                (row, rowNumber) -> row.getObject(1, UUID.class),
                UUID.fromString(savedModel.id())
            );
            jdbcTemplate.update(
                """
                insert into modeling_dbt_artifact (
                    id, model_spec_id, plan_id, project_key, dbt_unique_id, artifact_key,
                    artifact_type, path, content_checksum, content, status, revision,
                    model_checksum, ownership, idempotency_key, implementation_revision,
                    node_kind, materialization, created_date, last_modified_date
                ) values
                    (?, ?, ?, 'legacy-pjm', 'model.legacy.project_node',
                     'SQL:model.legacy.project_node', 'SQL', 'models/imported-project-node.sql',
                     ?, 'select imported_project_no from ods_project_node', 'IMPORTED', ?, ?,
                     'DBT_MANAGED', ?, 1, 'MODEL', 'table', now(), now()),
                    (?, ?, ?, 'legacy-pjm', 'model.legacy.project_node',
                     'SCHEMA:model.legacy.project_node', 'SCHEMA', 'models/imported-project-node.sql#schema',
                     ?, '{"columns":[]}', 'IMPORTED', ?, ?,
                     'DBT_MANAGED', ?, 1, 'MODEL', 'table', now(), now())
                """,
                UUID.randomUUID(),
                UUID.fromString(savedModel.id()),
                artifactPlanId,
                com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum.sha256Text(
                    "select imported_project_no from ods_project_node"
                ),
                savedModel.revision(),
                modelChecksum,
                "imported-sql-" + modelKey,
                UUID.randomUUID(),
                UUID.fromString(savedModel.id()),
                artifactPlanId,
                com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum.sha256Text(
                    "{\"columns\":[]}"
                ),
                savedModel.revision(),
                modelChecksum,
                "imported-schema-" + modelKey
            );
            DbtModelingContract.ImportResult imported = service.importDbt(
                tenant,
                new DbtModelingContract.ManifestImportRequest(
                    "legacy-pjm",
                    "1.7",
                    "model.legacy.project_node",
                    manifest,
                    null,
                    "import-" + modelKey,
                    savedModel.id(),
                    savedModel.revision(),
                    modelChecksum,
                    1,
                    implementationChecksum
                )
            );
            assertThat(imported.modelSpecId()).isEqualTo(savedModel.id());
            assertThat(imported.status()).isEqualTo("COMPILED");
            assertThat(service.artifacts(tenant, savedModel.id()))
                .extracting(ModelingVNextApplicationService.Artifact::artifactType)
                .containsExactlyInAnyOrder("SQL", "SCHEMA");
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from modeling_dbt_artifact
                     where model_spec_id = ? and revision = ? and implementation_revision = 1
                       and project_key = 'legacy-pjm'
                       and dbt_unique_id = 'model.legacy.project_node'
                       and node_kind = 'MODEL' and status = 'COMPILED'
                    """,
                    Integer.class,
                    UUID.fromString(savedModel.id()),
                    savedModel.revision()
                )
            )
                .isEqualTo(2);
            node.put("raw_code", "select changed_project_no from ods_project_node");
            assertThatThrownBy(() ->
                service.importDbt(
                    tenant,
                    new DbtModelingContract.ManifestImportRequest(
                        "legacy-pjm",
                        "1.7",
                        "model.legacy.project_node",
                        manifest,
                        null,
                        "import-changed-" + modelKey,
                        savedModel.id(),
                        savedModel.revision(),
                        modelChecksum,
                        1,
                        implementationChecksum
                    )
                )
            )
                .isInstanceOf(ModelingVNextApplicationService.DomainException.class)
                .extracting(error -> ((ModelingVNextApplicationService.DomainException) error).code())
                .isEqualTo("MODEL_ARTIFACT_REVISION_CONFLICT");
            assertThat(service.artifacts("tenant-other", savedModel.id())).isEmpty();
            ModelingVNextApplicationService.ReleaseGateView releaseGate = service.releaseGate(tenant, savedModel.id());
            assertThat(releaseGate.publishable()).isFalse();
            assertThat(releaseGate.blockers()).contains("DBT_TEST_FAILED");
            ModelingVNextApplicationService.LineageView lineage = service.lineage(tenant, savedModel.id());
            assertThat(lineage.nodes()).extracting(lineageNode -> lineageNode.get("id")).contains(savedModel.id(), "ods_project_node");
            assertThat(lineage.edges()).anyMatch(edge -> savedModel.id().equals(edge.get("to")) && "SOURCE".equals(edge.get("type")));

            jdbcTemplate.update(
                "update modeling_model_spec set status = 'PUBLISHED' where tenant_id = ? and id = ?",
                tenant,
                UUID.fromString(savedModel.id())
            );

            ModelingRunRequestContract.RunRequest runRequest = new ModelingRunRequestContract.RunRequest(
                savedModel.id(), 1, "run-" + modelKey,
                new ModelingRunRequestContract.ExternalContext(null, null, "dts_modeling", null, null, "model.project_node_detail", "public.project_node_detail")
            );
            ModelingVNextApplicationService.RunView run = service.createRun(tenant, runRequest);
            assertThat(run.state()).isEqualTo(ModelingRunStateMachine.RunState.QUEUED.name());
            assertThat(run.revision()).isEqualTo(savedModel.revision());
            assertThat(run.modelChecksum()).isNotBlank();
            assertThat(run.repairPath()).contains(savedModel.id());
            assertThat(service.getRun(tenant, run.id()).dbtSelector()).isEqualTo("model.project_node_detail");
            ModelingVNextApplicationService.RunView running = service.callbackRun(
                tenant,
                run.id(),
                new ModelingRunCallbackContract.Callback("RUNNING", "addax-pjm-1", "airflow-pjm-1", null, "dbt started")
            );
            assertThat(running.state()).isEqualTo(ModelingRunStateMachine.RunState.RUNNING.name());
            ModelingVNextApplicationService.RunView succeeded = service.callbackRun(
                tenant,
                run.id(),
                new ModelingRunCallbackContract.Callback("SUCCEEDED", null, null, "dbt-pjm-1", "dbt finished")
            );
            assertThat(succeeded.state()).isEqualTo(ModelingRunStateMachine.RunState.SUCCEEDED.name());
            assertThat(service.callbackRun(
                tenant,
                run.id(),
                new ModelingRunCallbackContract.Callback("FAILED", null, null, null, "late failure")
            ).state()).isEqualTo(ModelingRunStateMachine.RunState.SUCCEEDED.name());
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.update("delete from modeling_pipeline_run where tenant_id = ?", tenant);
                jdbcTemplate.update("delete from modeling_dbt_artifact where model_spec_id = ?", externalUuid(modelKey));
                jdbcTemplate.update("delete from modeling_dbt_artifact where project_key = ?", "legacy-pjm");
                jdbcTemplate.update("delete from modeling_model_implementation_revision where tenant_id = ?", tenant);
                jdbcTemplate.update("delete from modeling_model_implementation where tenant_id = ?", tenant);
                jdbcTemplate.update("delete from modeling_model_spec_revision where model_spec_id = ?", externalUuid(modelKey));
                jdbcTemplate.update("delete from modeling_model_spec where id = ?", externalUuid(modelKey));
                jdbcTemplate.update("delete from modeling_business_object where id = ?", externalUuid(objectKey));
            });
        }
    }

    private static UUID externalUuid(String value) {
        return UUID.nameUUIDFromBytes(("modeling:" + value).getBytes(StandardCharsets.UTF_8));
    }
}
