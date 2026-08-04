package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.config.InfraSecurityProperties;
import com.yuzhi.dts.ingestion.domain.IngestionAccessDefaultPolicy;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import com.yuzhi.dts.ingestion.repository.IngestionAccessDefaultPolicyRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRevisionRepository;
import com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionAccessContractServiceTest {

    @Mock
    private IngestionAccessDefaultPolicyRepository policyRepository;

    @Mock
    private IngestionTaskRevisionRepository revisionRepository;

    @Mock
    private IngestionTaskRepository taskRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IngestionAccessContractService service;

    @BeforeEach
    void setUp() {
        InfraSecurityProperties properties = new InfraSecurityProperties();
        properties.setEncryptionKey(
            Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
            )
        );
        properties.setKeyVersion("test-v1");
        InfraSettingsCryptoService cryptoService = new InfraSettingsCryptoService(properties);
        cryptoService.init();
        service = new IngestionAccessContractService(
            policyRepository,
            revisionRepository,
            taskRepository,
            objectMapper,
            cryptoService
        );
    }

    @Test
    void recordDraftRevisionShouldResolveDefaultsAndRemoveSecrets() {
        IngestionTask task = task(41L, "mysqlreader");
        task.setSourceConfig(
            objectMapper.createObjectNode()
                .put("schema", "finance")
                .put("password", "do-not-store")
                .set("headers", objectMapper.createObjectNode().put("X-API-Key", "also-do-not-store"))
        );
        when(revisionRepository.findAllByTaskIdForUpdate(41L)).thenReturn(List.of());
        when(policyRepository.findFirstByPolicyKeyAndStatusOrderByVersionDesc("GLOBAL", "ACTIVE"))
            .thenReturn(Optional.of(policy()));
        when(revisionRepository.save(any(IngestionTaskRevision.class))).thenAnswer(invocation -> {
            IngestionTaskRevision revision = invocation.getArgument(0);
            revision.setId(101L);
            return revision;
        });

        IngestionTaskRevision revision = service.recordDraftRevision(
            task,
            "dataset:00000000-0000-0000-0000-000000000041"
        );

        assertThat(revision.getRevisionNumber()).isEqualTo(1);
        assertThat(revision.getState()).isEqualTo("DRAFT");
        assertThat(revision.getSourceKind()).isEqualTo("database");
        assertThat(revision.getEffectiveConfigChecksum()).hasSize(64);
        assertThat(revision.getTaskSnapshot().toString())
            .doesNotContain("do-not-store")
            .doesNotContain("also-do-not-store")
            .doesNotContain("password")
            .doesNotContain("X-API-Key");
        assertThat(revision.getRuntimeSnapshot()).isNotEmpty();
        assertThat(new String(revision.getRuntimeSnapshot(), StandardCharsets.UTF_8))
            .doesNotContain("do-not-store")
            .doesNotContain("also-do-not-store");
        assertThat(revision.getRuntimeSnapshotKeyVersion()).isEqualTo("test-v1");
        assertThat(revision.getQualityPolicyRef()).isEqualTo("dataset:00000000-0000-0000-0000-000000000041");
    }

    @Test
    void materializeLatestDraftShouldRestoreEncryptedPlanWithoutMutatingActiveTask() {
        IngestionTask active = task(46L, "mysqlreader");
        active.setStatus("active");
        active.setSourceConfig(objectMapper.createObjectNode().put("schema", "active_schema"));
        IngestionTask draft = task(46L, "mysqlreader");
        draft.setSourceConfig(
            objectMapper.createObjectNode().put("schema", "draft_schema").put("password", "draft-secret")
        );
        when(revisionRepository.findAllByTaskIdForUpdate(46L)).thenReturn(List.of());
        when(policyRepository.findFirstByPolicyKeyAndStatusOrderByVersionDesc("GLOBAL", "ACTIVE"))
            .thenReturn(Optional.of(policy()));
        when(revisionRepository.save(any(IngestionTaskRevision.class))).thenAnswer(invocation -> {
            IngestionTaskRevision revision = invocation.getArgument(0);
            revision.setId(501L);
            return revision;
        });
        IngestionTaskRevision revision = service.recordDraftRevision(draft, null, false);
        when(revisionRepository.findFirstByTaskIdAndStateOrderByRevisionNumberDesc(46L, "DRAFT"))
            .thenReturn(Optional.of(revision));

        IngestionTask restored = service.materializeLatestDraft(active);

        assertThat(restored).isNotSameAs(active);
        assertThat(restored.getStatus()).isEqualTo("draft");
        assertThat(restored.getSourceConfig().path("schema").asText()).isEqualTo("draft_schema");
        assertThat(restored.getSourceConfig().path("password").asText()).isEqualTo("draft-secret");
        assertThat(active.getSourceConfig().path("schema").asText()).isEqualTo("active_schema");
    }

    @Test
    void draftDetailOverlayMustKeepActiveCompatibilityLifecycleExecutable() {
        IngestionTask task = task(48L, "mysqlreader");
        task.setStatus("active");
        IngestionTaskRevision draft = revision(task, 601L, 13, "DRAFT");
        draft.setTaskSnapshot(objectMapper.createObjectNode().put("name", "draft-r13-name"));
        draft.setEffectiveConfig(objectMapper.createObjectNode());
        when(revisionRepository.findFirstByTaskIdAndStateOrderByRevisionNumberDesc(48L, "DRAFT"))
            .thenReturn(Optional.of(draft));
        com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO dto = new com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO();
        dto.setId(48L);
        dto.setName("active-r12-name");
        dto.setStatus("active");

        service.enrichTaskDto(dto);

        assertThat(dto.getStatus()).isEqualTo("active");
        assertThat(dto.getName()).isEqualTo("draft-r13-name");
        assertThat(dto.getRevisionState()).isEqualTo("DRAFT");
        assertThat(dto.getRevisionNumber()).isEqualTo(13);
    }

    @Test
    void activateDraftRevisionShouldSupersedePreviousActiveAtomically() {
        IngestionTask task = task(42L, "httpreader");
        IngestionTaskRevision active = revision(task, 201L, 1, "ACTIVE");
        IngestionTaskRevision draft = revision(task, 202L, 2, "DRAFT");
        when(revisionRepository.findAllByTaskIdForUpdate(42L)).thenReturn(List.of(active, draft));
        when(revisionRepository.save(draft)).thenReturn(draft);

        IngestionTaskRevision activated = service.activateDraftRevision(42L);

        assertThat(activated).isSameAs(draft);
        assertThat(active.getState()).isEqualTo("SUPERSEDED");
        assertThat(draft.getState()).isEqualTo("ACTIVE");
        assertThat(draft.getActivatedAt()).isNotNull();
        verify(revisionRepository).saveAll(List.of(active));
        verify(revisionRepository).flush();
        verify(revisionRepository).save(draft);
    }

    @Test
    void refreshDraftRuntimeSnapshotShouldReplaceLegacyClassificationEvidenceWithCanonicalPlan() {
        IngestionTask draftPlan = task(50L, "mysqlreader");
        draftPlan.setClassificationSeal(objectMapper.createObjectNode().put("sealId", "legacy-seal"));
        draftPlan.setFieldClassifications(objectMapper.createObjectNode().put("customer_id", "SECRET"));
        when(revisionRepository.findAllByTaskIdForUpdate(50L)).thenReturn(List.of());
        when(policyRepository.findFirstByPolicyKeyAndStatusOrderByVersionDesc("GLOBAL", "ACTIVE"))
            .thenReturn(Optional.of(policy()));
        when(revisionRepository.save(any(IngestionTaskRevision.class))).thenAnswer(invocation -> {
            IngestionTaskRevision revision = invocation.getArgument(0);
            revision.setId(550L);
            return revision;
        });
        IngestionTaskRevision revision = service.recordDraftRevision(draftPlan, null, false);
        String legacyChecksum = revision.getEffectiveConfigChecksum();

        draftPlan.setClassificationSeal(null);
        draftPlan.setFieldClassifications(null);
        when(revisionRepository.findAllByTaskIdForUpdate(50L)).thenReturn(List.of(revision));

        IngestionTaskRevision refreshed = service.refreshDraftRuntimeSnapshot(50L, draftPlan);

        assertThat(refreshed.getClassificationSeal()).isNull();
        assertThat(refreshed.getFieldClassifications()).isNull();
        assertThat(refreshed.getTaskSnapshot().has("classificationSeal")).isFalse();
        assertThat(refreshed.getTaskSnapshot().has("fieldClassifications")).isFalse();
        assertThat(refreshed.getEffectiveConfig().path("task").has("classificationSeal")).isFalse();
        assertThat(refreshed.getEffectiveConfig().path("task").has("fieldClassifications")).isFalse();
        assertThat(refreshed.getEffectiveConfigChecksum()).hasSize(64).isNotEqualTo(legacyChecksum);
    }

    @Test
    void bindActiveRevisionShouldFreezeExecutionContract() {
        IngestionTask task = task(43L, "excelreader");
        task.setStatus("active");
        IngestionTaskRevision active = revision(task, 301L, 7, "ACTIVE");
        active.setEffectiveConfigChecksum("checksum-7");
        active.setQualityPolicyRef("dataset:00000000-0000-0000-0000-000000000043");
        when(revisionRepository.findFirstByTaskIdAndStateOrderByRevisionNumberDesc(43L, "ACTIVE"))
            .thenReturn(Optional.of(active));
        IngestionExecution execution = new IngestionExecution();

        service.bindActiveRevision(execution, task);

        assertThat(execution.getTaskRevisionId()).isEqualTo(301L);
        assertThat(execution.getRevisionNumber()).isEqualTo(7);
        assertThat(execution.getEffectiveConfigChecksum()).isEqualTo("checksum-7");
        assertThat(execution.getQualityPolicyRef()).isEqualTo("dataset:00000000-0000-0000-0000-000000000043");
    }

    @Test
    void invalidQualityPolicyReferenceShouldFailClosed() {
        IngestionTask task = task(44L, "mysqlreader");
        when(revisionRepository.findAllByTaskIdForUpdate(44L)).thenReturn(List.of());
        when(policyRepository.findFirstByPolicyKeyAndStatusOrderByVersionDesc("GLOBAL", "ACTIVE"))
            .thenReturn(Optional.of(policy()));

        assertThatThrownBy(() -> service.recordDraftRevision(task, "00000000-0000-0000-0000-000000000044"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dataset:<uuid>");
    }

    @Test
    void sourceChangeShouldNotInheritPreviousQualityBinding() {
        IngestionTask task = task(45L, "mysqlreader");
        IngestionTaskRevision previous = revision(task, 401L, 1, "ACTIVE");
        previous.setQualityPolicyRef("dataset:00000000-0000-0000-0000-000000000099");
        when(revisionRepository.findAllByTaskIdForUpdate(45L)).thenReturn(List.of(previous));
        when(policyRepository.findFirstByPolicyKeyAndStatusOrderByVersionDesc("GLOBAL", "ACTIVE"))
            .thenReturn(Optional.of(policy()));
        when(revisionRepository.save(any(IngestionTaskRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngestionTaskRevision revision = service.recordDraftRevision(task, null, false);

        assertThat(revision.getRevisionNumber()).isEqualTo(2);
        assertThat(revision.getQualityPolicyRef()).isNull();
    }

    @Test
    void executionMustMaterializeItsBoundRevisionAfterAReplacementDraftIsCreated() {
        IngestionTask canonical = task(47L, "mysqlreader");
        canonical.setStatus("active");
        canonical.setSourceConfig(objectMapper.createObjectNode().put("schema", "r12_schema"));
        canonical.setAddaxJobPath("/jobs/task-47-r12.json");
        canonical.setAirflowDagId("task_47_r12");

        when(taskRepository.getReferenceById(47L)).thenReturn(canonical);
        when(policyRepository.findFirstByPolicyKeyAndStatusOrderByVersionDesc("GLOBAL", "ACTIVE"))
            .thenReturn(Optional.of(policy()));
        when(revisionRepository.findAllByTaskIdForUpdate(47L)).thenReturn(List.of());
        when(revisionRepository.save(any(IngestionTaskRevision.class))).thenAnswer(invocation -> {
            IngestionTaskRevision revision = invocation.getArgument(0);
            revision.setId(revision.getRevisionNumber() == 1 ? 512L : 513L);
            return revision;
        });

        IngestionTaskRevision r12 = service.recordDraftRevision(canonical, null, false);
        canonical.setSourceConfig(objectMapper.createObjectNode().put("schema", "r13_schema"));
        canonical.setAddaxJobPath("/jobs/task-47-r13.json");
        canonical.setAirflowDagId("task_47_r13");
        when(revisionRepository.findAllByTaskIdForUpdate(47L)).thenReturn(List.of(r12));

        IngestionTaskRevision r13 = service.recordDraftRevision(canonical, null, false);
        r13.setState("ACTIVE");
        when(revisionRepository.findById(512L)).thenReturn(Optional.of(r12));

        IngestionExecution execution = new IngestionExecution();
        execution.setTask(canonical);
        execution.setTaskRevisionId(512L);
        IngestionTask runtime = service.materializeExecutionTask(canonical, execution.getTaskRevisionId());

        assertThat(runtime.getSourceConfig().path("schema").asText()).isEqualTo("r12_schema");
        assertThat(runtime.getAddaxJobPath()).isEqualTo("/jobs/task-47-r12.json");
        assertThat(runtime.getAirflowDagId()).isEqualTo("task_47_r12");
        assertThat(canonical.getSourceConfig().path("schema").asText()).isEqualTo("r13_schema");
        assertThat(r13.getId()).isEqualTo(513L);
    }

    @Test
    void materializeExecutionTaskShouldPreserveNullableBooleanFallbacksWhenSnapshotOmitsThem() {
        IngestionTask canonical = task(49L, "mysqlreader");
        canonical.setAirflowEnabled(null);
        canonical.setQualityPreCheckEnabled(null);
        when(taskRepository.getReferenceById(49L)).thenReturn(canonical);
        when(revisionRepository.findAllByTaskIdForUpdate(49L)).thenReturn(List.of());
        when(policyRepository.findFirstByPolicyKeyAndStatusOrderByVersionDesc("GLOBAL", "ACTIVE"))
            .thenReturn(Optional.of(policy()));
        when(revisionRepository.save(any(IngestionTaskRevision.class))).thenAnswer(invocation -> {
            IngestionTaskRevision revision = invocation.getArgument(0);
            revision.setId(514L);
            return revision;
        });

        IngestionTaskRevision revision = service.recordDraftRevision(canonical, null, false);
        when(revisionRepository.findById(514L)).thenReturn(Optional.of(revision));

        IngestionTask runtime = service.materializeExecutionTask(canonical, 514L);

        assertThat(runtime.getAirflowEnabled()).isNull();
        assertThat(runtime.getQualityPreCheckEnabled()).isNull();
    }

    private IngestionTask task(Long id, String sourceType) {
        IngestionTask task = new IngestionTask();
        task.setId(id);
        task.setName("task-" + id);
        task.setSourceType(sourceType);
        task.setSyncMode("full_refresh");
        task.setStatus("draft");
        return task;
    }

    private IngestionTaskRevision revision(IngestionTask task, Long id, int number, String state) {
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(id);
        revision.setTask(task);
        revision.setRevisionNumber(number);
        revision.setState(state);
        revision.setSourceKind("database");
        revision.setCreatedBy("tester");
        revision.setCreatedAt(Instant.now());
        return revision;
    }

    private IngestionAccessDefaultPolicy policy() {
        IngestionAccessDefaultPolicy policy = new IngestionAccessDefaultPolicy();
        policy.setId(1L);
        policy.setPolicyKey("GLOBAL");
        policy.setVersion(1);
        policy.setStatus("ACTIVE");
        policy.setDefaults(
            objectMapper.createObjectNode()
                .set("common", objectMapper.createObjectNode().put("taskConcurrency", 1))
        );
        policy.setChecksum("policy-checksum");
        policy.setActivatedAt(Instant.now());
        return policy;
    }
}
