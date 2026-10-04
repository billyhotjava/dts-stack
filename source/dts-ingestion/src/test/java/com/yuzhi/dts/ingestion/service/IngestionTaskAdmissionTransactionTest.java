package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionAccessDefaultPolicy;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionAccessDefaultPolicyRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.infra.InfraServiceSettingsRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "dts.platform.infra.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@ActiveProfiles("test")
class IngestionTaskAdmissionTransactionTest {

    @Autowired
    private IngestionTaskService ingestionTaskService;

    @Autowired
    private IngestionTaskRepository taskRepository;

    @Autowired
    private IngestionAccessDefaultPolicyRepository policyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AddaxJobService addaxJobService;

    @MockBean
    private InfraServiceSettingsRepository infraServiceSettingsRepository;

    @MockBean
    private IngestionClassificationSealGuard classificationSealGuard;

    @MockBean
    private IngestionTaskSecretMigrationService secretMigrationService;

    @AfterEach
    void cleanUp() {
        taskRepository.deleteAll();
        policyRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admission-test")
    void admit_rollsBackActiveStatusWhenRequiredJobGenerationFails() {
        IngestionAccessDefaultPolicy policy = new IngestionAccessDefaultPolicy();
        policy.setPolicyKey("GLOBAL");
        policy.setVersion(1);
        policy.setStatus("ACTIVE");
        policy.setDefaults(objectMapper.createObjectNode()
            .set("common", objectMapper.createObjectNode().put("taskConcurrency", 1)));
        policy.setChecksum("admission-policy-checksum");
        policy.setActivatedAt(Instant.now());
        policy.setCreatedBy("admission-test");
        policy.setCreatedAt(Instant.now());
        policyRepository.saveAndFlush(policy);

        ObjectNode seal = validSeal();
        ObjectNode fields = objectMapper.createObjectNode();
        IngestionTask task = new IngestionTask();
        task.setName("rollback-admission");
        task.setSourceType("mysqlreader");
        task.setDestinationType("postgresqlwriter");
        task.setSyncMode("full_refresh");
        task.setSyncSchedule("cron:0 0 * * *");
        task.setStatus("draft");
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);
        Long taskId = taskRepository.saveAndFlush(task).getId();
        IngestionTask storedDraft = taskRepository.findById(taskId).orElseThrow();
        JsonNode storedSeal = storedDraft.getClassificationSeal();
        JsonNode storedFields = storedDraft.getFieldClassifications();

        doThrow(new IllegalStateException("forced job generation failure"))
            .when(addaxJobService)
            .createJobFromTask(any(IngestionTask.class));

        assertThatThrownBy(() -> ingestionTaskService.admit(taskId, storedSeal, storedFields))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("forced job generation failure");

        IngestionTask persisted = taskRepository.findById(taskId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("draft");
        assertThat(persisted.getClassificationSeal()).isEqualTo(storedSeal);
        assertThat(persisted.getAddaxJobPath()).isNull();
        assertThat(persisted.getAirflowDagId()).isNull();
    }

    private ObjectNode validSeal() {
        ObjectNode seal = objectMapper.createObjectNode();
        seal.put("sealId", "seal-rollback-001");
        seal.put("subjectType", "ASSET");
        seal.put("subjectKey", "ingestion-task:rollback");
        seal.put("effectiveLevel", "INTERNAL");
        seal.put("snapshotVersion", 1L);
        seal.put("checksum", "0123456789abcdef0123456789abcdef");
        seal.put("sealedAt", "2026-07-28T00:00:00Z");
        return seal;
    }
}
