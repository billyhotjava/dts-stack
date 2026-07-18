package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

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

/** PostgreSQL-backed acceptance of the modeling ledger, dbt artifact and run boundary. */
@IntegrationTest
class ModelingVNextApplicationServiceIT {

    @Autowired
    private ModelingVNextApplicationService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void pjmGoldenPathPersistsCompilesImportsAndQueuesRun() {
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
            ModelingVNextContract.ModelType.FACT, ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED,
            "project_node_detail_" + seed.toString().substring(0, 8), object.grain(),
            List.of(new ModelingVNextContract.StandardBinding("project_no", "std.project.code", null, null)),
            object.sourceRefs(), List.of(), List.of("node_count"), "table", 1
        );

        try {
            ModelingVNextContract.BusinessObject savedObject = service.saveBusinessObject(tenant, object, 1, "bo-" + objectKey);
            assertThat(service.listBusinessObjects(tenant, object.processId(), "DRAFT")).extracting(ModelingVNextContract.BusinessObject::id).contains(savedObject.id());

            ModelingVNextContract.ModelSpec savedModel = service.saveModelSpec(tenant, model, 1, "model-" + modelKey);
            ModelingVNextApplicationService.CompileResult compiled = service.compile(tenant, savedModel.id(), 1, "compile-" + modelKey);
            assertThat(compiled.status()).isEqualTo("COMPILED");
            assertThat(compiled.artifacts()).extracting(ModelingVNextApplicationService.Artifact::artifactType).contains("SQL", "SCHEMA", "TEST");
            ModelingVNextApplicationService.ReleaseGateView releaseGate = service.releaseGate(tenant, savedModel.id());
            assertThat(releaseGate.publishable()).isTrue();
            assertThat(releaseGate.status()).isEqualTo("RELEASE_READY");
            assertThat(service.artifacts(tenant, savedModel.id())).filteredOn(a -> "SQL".equals(a.artifactType())).singleElement()
                .extracting(ModelingVNextApplicationService.Artifact::checksum).isEqualTo(
                    compiled.artifacts().stream().filter(a -> "SQL".equals(a.artifactType())).findFirst().orElseThrow().checksum()
                );
            assertThat(service.artifacts("tenant-other", savedModel.id())).isEmpty();
            assertThat(service.drift(tenant, savedModel.id()).status()).isEqualTo("CLEAN");
            jdbcTemplate.update(
                "update modeling_dbt_artifact set content = ?, content_checksum = ? where model_spec_id = ? and artifact_type = 'SQL'",
                "select tampered_project_no from ods_project_node",
                "tampered-checksum",
                externalUuid(modelKey)
            );
            assertThat(service.drift(tenant, savedModel.id()).status()).isEqualTo("DRIFTED");
            assertThat(service.releaseGate(tenant, savedModel.id()).publishable()).isFalse();
            assertThat(service.releaseGate(tenant, savedModel.id()).blockers()).contains("ARTIFACT_CHECKSUM_DRIFT");
            ModelingVNextApplicationService.LineageView lineage = service.lineage(tenant, savedModel.id());
            assertThat(lineage.nodes()).extracting(node -> node.get("id")).contains(savedModel.id(), "ods_project_node");
            assertThat(lineage.edges()).anyMatch(edge -> savedModel.id().equals(edge.get("to")) && "SOURCE".equals(edge.get("type")));

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("resource_type", "model");
            node.put("name", "legacy_project_node");
            node.put("raw_code", "select project_no from ods_project_node");
            node.put("columns", Map.of("project_no", Map.of("name", "project_no")));
            node.put("depends_on", Map.of("nodes", List.of("source.project.ods_project_node")));
            Map<String, Object> manifest = Map.of("nodes", Map.of("model.legacy.project_node", node));
            DbtModelingContract.ImportResult imported = service.importDbt(
                tenant,
                new DbtModelingContract.ManifestImportRequest("legacy-pjm", "1.7", "model.legacy.project_node", manifest, null, "import-" + modelKey)
            );
            assertThat(imported.status()).isEqualTo("LEGACY_READONLY");
            assertThat(service.artifacts(tenant, savedModel.id())).isNotEmpty();

            ModelingRunRequestContract.RunRequest runRequest = new ModelingRunRequestContract.RunRequest(
                savedModel.id(), 1, "run-" + modelKey,
                new ModelingRunRequestContract.ExternalContext(null, null, "dts_modeling", null, null, "model.project_node_detail", "public.project_node_detail")
            );
            ModelingVNextApplicationService.RunView run = service.createRun(tenant, runRequest);
            assertThat(run.state()).isEqualTo(ModelingRunStateMachine.RunState.QUEUED.name());
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
            jdbcTemplate.update("delete from modeling_pipeline_run where tenant_id = ?", tenant);
            jdbcTemplate.update("delete from modeling_dbt_artifact where model_spec_id = ?", externalUuid(modelKey));
            jdbcTemplate.update("delete from modeling_dbt_artifact where project_key = ?", "legacy-pjm");
            jdbcTemplate.update("delete from modeling_model_spec_revision where model_spec_id = ?", externalUuid(modelKey));
            jdbcTemplate.update("delete from modeling_model_spec where id = ?", externalUuid(modelKey));
            jdbcTemplate.update("delete from modeling_business_object where id = ?", externalUuid(objectKey));
        }
    }

    private static UUID externalUuid(String value) {
        return UUID.nameUUIDFromBytes(("modeling:" + value).getBytes(StandardCharsets.UTF_8));
    }
}
