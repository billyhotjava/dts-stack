package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDesignDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDesignUpdateRequest;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionTaskDesignServiceTest {

    @Mock
    private IngestionTaskQueryService taskQueryService;

    @Mock
    private IngestionTaskService taskService;

    @Mock
    private IngestionTaskRepository taskRepository;

    @Mock
    private AirflowDagService airflowDagService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IngestionTaskDesignService service;

    @BeforeEach
    void setUp() {
        service = new IngestionTaskDesignService(
            taskQueryService,
            taskService,
            taskRepository,
            airflowDagService,
            objectMapper
        );
    }

    @Test
    void databaseLandingDesignDoesNotRequireAnAssetBeforeItsFirstExecution() {
        IngestionTaskDTO task = taskDto();
        task.setSourceType("mysqlreader");
        task.setTargetDatasetId(null);
        task.setQualityPolicyRef(null);
        task.setDestinationConfig(objectMapper.createObjectNode()
            .put("targetDataSourceId", "a0000000-0000-0000-0000-000000000001"));
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(task));

        IngestionTaskDesignDTO design = service.getDesign(13L);
        assertThat(design.validation().valid()).isTrue();
        assertThat(design.destination().assetRef().datasetId()).isNull();
        assertThat(design.postIngestionQuality().enabled()).isFalse();
    }

    @Test
    void designValidationAcceptsPersistedScheduleFormatsAndRejectsInvalidIntervals() {
        IngestionTaskDTO task = taskDto();
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(task));
        for (String schedule : java.util.List.of("manual", "interval:15", "cron:0 0 2 * * *", "0 0 2 * * *")) {
            task.setSyncSchedule(schedule);
            assertThat(service.getDesign(13L).validation().valid()).as(schedule).isTrue();
        }
        for (String schedule : java.util.List.of("interval:0", "interval:abc", "cron:invalid")) {
            task.setSyncSchedule(schedule);
            assertThat(service.getDesign(13L).validation().valid()).as(schedule).isFalse();
        }
    }

    @Test
    void getDesignUsesVersionedTaskAsSingleSourceAndGeneratesReadonlyTopology() {
        IngestionTaskDTO task = taskDto();
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(task));

        IngestionTaskDesignDTO design = service.getDesign(13L);

        assertThat(design.taskId()).isEqualTo(13L);
        assertThat(design.planChecksum()).isEqualTo("checksum-r4");
        assertThat(design.destination().assetRef().datasetId())
            .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000013"));
        assertThat(design.topology().readonly()).isTrue();
        assertThat(design.topology().planChecksum()).isEqualTo("checksum-r4");
        assertThat(design.topology().nodes()).extracting(IngestionTaskDesignDTO.TopologyNode::kind)
            .containsExactly("SOURCE", "LOAD", "ASSET_OBSERVATION", "POST_INGESTION_QUALITY", "END");
        assertThat(design.legacyDsl().present()).isTrue();
        assertThat(design.legacyDsl().publishable()).isFalse();
    }

    @Test
    void getDesignKeepsLegacyQualityDatasetReferenceValidDuringNullableColumnExpansion() {
        IngestionTaskDTO task = taskDto();
        task.setTargetDatasetId(null);
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(task));

        IngestionTaskDesignDTO design = service.getDesign(13L);

        assertThat(design.destination().assetRef().datasetId())
            .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000013"));
        assertThat(design.validation().valid()).isTrue();
    }

    @Test
    void saveDesignRejectsStaleChecksumBeforeUpdatingTask() {
        when(taskRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(new IngestionTask()));
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(taskDto()));

        assertThatThrownBy(() -> service.saveDesign(13L, "stale-checksum", updateRequest(true)))
            .isInstanceOf(IngestionTaskDesignService.DesignConflictException.class)
            .hasMessageContaining("TASK_DESIGN_CONFLICT");

        verify(taskService, never()).updateDesign(any(), any(), anyBoolean());
    }

    @Test
    void saveDesignForwardsOnlyTypedFieldsAndCanExplicitlyDisableQuality() {
        IngestionTaskDTO current = taskDto();
        IngestionTaskDTO updated = taskDto();
        updated.setEffectiveConfigChecksum("checksum-r5");
        updated.setQualityPolicyRef(null);
        when(taskRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(new IngestionTask()));
        when(taskQueryService.findOne(13L))
            .thenReturn(Optional.of(current))
            .thenReturn(Optional.of(updated));
        when(taskService.updateDesign(eq(13L), any(IngestionTaskDTO.class), eq(false))).thenReturn(updated);

        IngestionTaskDesignDTO saved = service.saveDesign(13L, "\"checksum-r4\"", updateRequest(false));

        org.mockito.InOrder compareAndSaveOrder = org.mockito.Mockito.inOrder(
            taskRepository,
            taskQueryService,
            taskService
        );
        compareAndSaveOrder.verify(taskRepository).findByIdForUpdate(13L);
        compareAndSaveOrder.verify(taskQueryService).findOne(13L);
        compareAndSaveOrder.verify(taskService).updateDesign(eq(13L), any(IngestionTaskDTO.class), eq(false));
        ArgumentCaptor<IngestionTaskDTO> patch = ArgumentCaptor.forClass(IngestionTaskDTO.class);
        verify(taskService).updateDesign(eq(13L), patch.capture(), eq(false));
        assertThat(patch.getValue().getName()).isEqualTo("API 成本中心接入");
        assertThat(patch.getValue().getStatus()).isNull();
        assertThat(patch.getValue().getGraphDsl()).isNull();
        assertThat(patch.getValue().getAirflowDagId()).isNull();
        assertThat(patch.getValue().getQualityPolicyRef()).isNull();
        assertThat(saved.planChecksum()).isEqualTo("checksum-r5");
        assertThat(saved.postIngestionQuality().enabled()).isFalse();
    }

    @Test
    void pauseScheduleUsesOnlyTaskOwnedDagAndPersistsOperationalState() {
        IngestionTask task = new IngestionTask();
        task.setId(13L);
        task.setName("api-task");
        task.setStatus("active");
        task.setAirflowDagId("ingestion_task_13_r4");
        when(taskRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        IngestionTaskDesignDTO.ScheduleCommand result = service.setSchedulePaused(13L, true);

        verify(airflowDagService).setDagPausedStrict("ingestion_task_13_r4", true);
        assertThat(task.getStatus()).isEqualTo("paused");
        assertThat(result.state()).isEqualTo("PAUSED");
    }

    @Test
    void repeatingTheSameScheduleCommandIsIdempotent() {
        IngestionTask task = new IngestionTask();
        task.setId(13L);
        task.setName("api-task");
        task.setStatus("paused");
        task.setAirflowDagId("ingestion_task_13_r4");
        when(taskRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(task));

        IngestionTaskDesignDTO.ScheduleCommand result = service.setSchedulePaused(13L, true);

        assertThat(result.state()).isEqualTo("PAUSED");
        verify(airflowDagService, never()).setDagPausedStrict(any(), anyBoolean());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void activeTopologyUsesExplicitActiveRevisionInsteadOfLatestDraft() {
        IngestionTaskDTO active = taskDto();
        active.setRevisionNumber(3);
        active.setRevisionState("ACTIVE");
        active.setEffectiveConfigChecksum("checksum-r3");
        when(taskQueryService.findOneActive(13L)).thenReturn(Optional.of(active));

        IngestionTaskDesignDTO.TopologyProjection topology = service.getTopology(13L, "ACTIVE");

        assertThat(topology.planChecksum()).isEqualTo("checksum-r3");
        verify(taskQueryService, never()).findOne(13L);
    }

    @Test
    void validateDesignRejectsMoreThanOneThousandMappings() {
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(taskDto()));
        ArrayNode mappings = objectMapper.createArrayNode();
        for (int index = 0; index < 1_001; index++) {
            mappings.add(objectMapper.createObjectNode().put("source", "source_" + index).put("target", "target_" + index));
        }

        IngestionTaskDesignDTO.ValidationResult result = service.validateDesign(
            13L,
            "checksum-r4",
            updateRequest(true, mappings)
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(IngestionTaskDesignDTO.ValidationIssue::code)
            .contains("TABLE_MAPPING_LIMIT_EXCEEDED");
    }

    @Test
    void validateDesignRejectsMalformedMappingEntries() {
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(taskDto()));
        ArrayNode mappings = objectMapper.createArrayNode()
            .add(objectMapper.createObjectNode().put("source", " ").put("target", "ods_api_cost_center"))
            .add("not-an-object");

        IngestionTaskDesignDTO.ValidationResult result = service.validateDesign(
            13L,
            "checksum-r4",
            updateRequest(true, mappings)
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(IngestionTaskDesignDTO.ValidationIssue::code)
            .contains("TABLE_MAPPING_SOURCE_REQUIRED", "TABLE_MAPPING_ENTRY_INVALID");
    }

    @Test
    void validateDesignRejectsUnsupportedSyncModeAndInvalidConfigShapes() {
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(taskDto()));
        IngestionTaskDesignUpdateRequest invalid = new IngestionTaskDesignUpdateRequest(
            "API 成本中心接入",
            "接入财务成本中心",
            UUID.fromString("10000000-0000-0000-0000-000000000013"),
            "httpreader",
            objectMapper.createArrayNode(),
            "postgresqlwriter",
            destinationConfig(),
            UUID.fromString("00000000-0000-0000-0000-000000000013"),
            "unsupported",
            "0 0 2 * * *",
            objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().put("source", "cost_centers").put("target", "ods_api_cost_center")
            ),
            objectMapper.createArrayNode(),
            true,
            "dataset:00000000-0000-0000-0000-000000000013"
        );

        IngestionTaskDesignDTO.ValidationResult result = service.validateDesign(13L, "checksum-r4", invalid);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(IngestionTaskDesignDTO.ValidationIssue::code)
            .contains("SOURCE_CONFIG_REQUIRED", "SYNC_MODE_INVALID", "SYNC_CONFIG_INVALID");
    }

    @Test
    void validateDesignRejectsNormalizedPayloadLargerThanOneMebibyte() {
        when(taskQueryService.findOne(13L)).thenReturn(Optional.of(taskDto()));
        IngestionTaskDesignUpdateRequest oversized = new IngestionTaskDesignUpdateRequest(
            "API 成本中心接入",
            "x".repeat(1_048_576),
            UUID.fromString("10000000-0000-0000-0000-000000000013"),
            "httpreader",
            objectMapper.createObjectNode().put("endpoint", "/api/cost-centers"),
            "postgresqlwriter",
            destinationConfig(),
            UUID.fromString("00000000-0000-0000-0000-000000000013"),
            "full_refresh",
            "0 0 2 * * *",
            objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().put("source", "cost_centers").put("target", "ods_api_cost_center")
            ),
            objectMapper.createObjectNode(),
            true,
            "dataset:00000000-0000-0000-0000-000000000013"
        );

        IngestionTaskDesignDTO.ValidationResult result = service.validateDesign(13L, "checksum-r4", oversized);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(IngestionTaskDesignDTO.ValidationIssue::code)
            .contains("DESIGN_PAYLOAD_TOO_LARGE");
    }

    private IngestionTaskDTO taskDto() {
        IngestionTaskDTO task = new IngestionTaskDTO();
        task.setId(13L);
        task.setName("API 成本中心接入");
        task.setDescription("接入财务成本中心");
        task.setSourceType("httpreader");
        task.setSourceDataSourceId(UUID.fromString("10000000-0000-0000-0000-000000000013"));
        task.setSourceConfig(objectMapper.createObjectNode().put("endpoint", "/api/cost-centers"));
        task.setDestinationType("postgresqlwriter");
        task.setDestinationConfig(destinationConfig());
        task.setTargetDatasetId(UUID.fromString("00000000-0000-0000-0000-000000000013"));
        task.setTableMapping(
            objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().put("source", "cost_centers").put("target", "ods_api_cost_center")
            )
        );
        task.setSyncMode("full_refresh");
        task.setSyncSchedule("0 0 2 * * *");
        task.setStatus("active");
        task.setRevisionNumber(4);
        task.setRevisionState("ACTIVE");
        task.setEffectiveConfigChecksum("checksum-r4");
        task.setQualityPolicyRef("dataset:00000000-0000-0000-0000-000000000013");
        task.setGraphDsl(objectMapper.createObjectNode().put("dslVersion", "1.0"));
        task.setAirflowDagId("ingestion_task_13_r4");
        task.setAirflowEnabled(true);
        return task;
    }

    private IngestionTaskDesignUpdateRequest updateRequest(boolean qualityEnabled) {
        return updateRequest(
            qualityEnabled,
            objectMapper.createArrayNode().add(
                objectMapper.createObjectNode().put("source", "cost_centers").put("target", "ods_api_cost_center")
            )
        );
    }

    private IngestionTaskDesignUpdateRequest updateRequest(boolean qualityEnabled, JsonNode tableMapping) {
        return new IngestionTaskDesignUpdateRequest(
            "API 成本中心接入",
            "接入财务成本中心",
            UUID.fromString("10000000-0000-0000-0000-000000000013"),
            "httpreader",
            objectMapper.createObjectNode().put("endpoint", "/api/cost-centers"),
            "postgresqlwriter",
            destinationConfig(),
            UUID.fromString("00000000-0000-0000-0000-000000000013"),
            "full_refresh",
            "0 0 2 * * *",
            tableMapping,
            objectMapper.createObjectNode(),
            qualityEnabled,
            qualityEnabled ? "dataset:00000000-0000-0000-0000-000000000013" : null
        );
    }

    private com.fasterxml.jackson.databind.node.ObjectNode destinationConfig() {
        com.fasterxml.jackson.databind.node.ObjectNode config = objectMapper.createObjectNode();
        config.put("targetDataSourceId", "a0000000-0000-0000-0000-000000000001");
        config.putArray("table").add("ods_api_cost_center");
        return config;
    }
}
