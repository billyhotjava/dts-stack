package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeMappingRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunItemRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardPackageApplyServiceTest {

    @Mock
    private StandardPackageImportRunRepository runRepository;

    @Mock
    private StandardPackageImportRunItemRepository runItemRepository;

    @Mock
    private ModelingGlossaryTermRepository glossaryTermRepository;

    @Mock
    private MetadataStandardRepository metadataStandardRepository;

    @Mock
    private MetadataStandardService metadataStandardService;

    @Mock
    private StdCodeDirectoryRepository codeDirectoryRepository;

    @Mock
    private StdCodeValueRepository codeValueRepository;

    @Mock
    private StdCodeMappingRepository codeMappingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StandardPackageApplyService service;

    @BeforeEach
    void setUp() {
        service = new StandardPackageApplyService(
            runRepository,
            runItemRepository,
            glossaryTermRepository,
            metadataStandardRepository,
            metadataStandardService,
            codeDirectoryRepository,
            codeValueRepository,
            codeMappingRepository,
            objectMapper
        );
    }

    private StandardPackageImportRun previewedRun(UUID runId, String payloadJson) {
        StandardPackageImportRun run = new StandardPackageImportRun();
        run.setId(runId);
        run.setPackageName("test.zip");
        run.setSource("UPLOAD");
        run.setStatus("PREVIEWED");
        run.setPreviewJson("{\"blocking\":false}");
        run.setPayloadJson(payloadJson);
        return run;
    }

    @Test
    @DisplayName("apply：按依赖序入库并记录 before-image，run 置 APPLIED")
    void apply_fullPackage_persistsAllTypesInOrder() {
        UUID runId = UUID.randomUUID();
        String payload =
            "{" +
            "\"terms\":[{\"code\":\"T1\",\"name\":\"术语一\"}]," +
            "\"elements\":[{\"fieldNameCn\":\"性别\",\"fieldNameEn\":\"gender\",\"dataType\":\"VARCHAR\",\"dataLength\":1," +
            "\"nullable\":false,\"domain\":\"人口\",\"description\":\"d\",\"sourceSystem\":\"MDM\",\"codeSet\":\"GENDER\",\"securityLevel\":\"INTERNAL\"}]," +
            "\"codeDirectories\":[{\"codeTypeCode\":\"GENDER\",\"codeTypeName\":\"性别代码\",\"status\":1}]," +
            "\"codeItems\":[{\"codeTypeCode\":\"GENDER\",\"codeValue\":\"1\",\"codeName\":\"男\"}]," +
            "\"codeMappings\":[{\"codeTypeCode\":\"GENDER\",\"sourceSystem\":\"LEGACY\",\"sourceCode\":\"M\",\"standardCode\":\"1\"}]" +
            "}";
        when(runRepository.findById(runId)).thenReturn(Optional.of(previewedRun(runId, payload)));
        when(glossaryTermRepository.findByCodeLowerIn(anyCollection())).thenReturn(List.of());
        when(glossaryTermRepository.save(any(ModelingGlossaryTerm.class))).thenAnswer(inv -> {
            ModelingGlossaryTerm term = inv.getArgument(0);
            term.setId(UUID.randomUUID());
            return term;
        });
        when(codeDirectoryRepository.findByCodeTypeCodeIgnoreCase("GENDER")).thenReturn(Optional.empty());
        when(codeDirectoryRepository.save(any(StdCodeDirectory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codeValueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc("GENDER")).thenReturn(List.of());
        AtomicLong itemIds = new AtomicLong(100);
        when(codeValueRepository.save(any(StdCodeValue.class))).thenAnswer(inv -> {
            StdCodeValue value = inv.getArgument(0);
            value.setItemId(itemIds.incrementAndGet());
            return value;
        });
        when(metadataStandardRepository.findByFieldNameEnIgnoreCaseAndDomainIgnoreCase("gender", "人口")).thenReturn(Optional.empty());
        MetadataStandardDto dto = new MetadataStandardDto();
        dto.setId(UUID.randomUUID());
        when(metadataStandardService.create(any(MetadataStandardUpsertRequest.class))).thenReturn(dto);
        when(codeMappingRepository.findByCodeTypeIdOrderBySourceSysAsc("GENDER")).thenReturn(List.of());
        when(codeMappingRepository.save(any())).thenAnswer(inv -> {
            var mapping = (com.yuzhi.dts.platform.domain.governance.StdCodeMapping) inv.getArgument(0);
            mapping.setMapId(9L);
            return mapping;
        });

        Map<String, Object> result = service.apply(runId, "tester");

        assertThat(result.get("status")).isEqualTo("APPLIED");
        assertThat(result.get("totalCreated")).isEqualTo(5);
        assertThat(result.get("totalUpdated")).isEqualTo(0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StandardPackageImportRunItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(runItemRepository).saveAll(captor.capture());
        List<StandardPackageImportRunItem> items = captor.getValue();
        assertThat(items).hasSize(5);
        assertThat(items.get(0).getEntityType()).isEqualTo("TERM");
        assertThat(items.get(1).getEntityType()).isEqualTo("CODE_DIRECTORY");
        assertThat(items.get(2).getEntityType()).isEqualTo("CODE_VALUE");
        assertThat(items.get(3).getEntityType()).isEqualTo("ELEMENT");
        assertThat(items.get(4).getEntityType()).isEqualTo("CODE_MAPPING");
        assertThat(items).allSatisfy(item -> assertThat(item.getAction()).isEqualTo("CREATE"));

        ArgumentCaptor<StandardPackageImportRun> runCaptor = ArgumentCaptor.forClass(StandardPackageImportRun.class);
        verify(runRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo("APPLIED");
    }

    @Test
    @DisplayName("apply：preview 存在错误时拒绝")
    void apply_blockingPreview_throws() {
        UUID runId = UUID.randomUUID();
        StandardPackageImportRun run = previewedRun(runId, "{}");
        run.setPreviewJson("{\"blocking\":true}");
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.apply(runId, "tester"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("校验报告存在错误");
        verify(runItemRepository, never()).saveAll(anyCollection());
    }

    @Test
    @DisplayName("apply：已应用的 run 不可重复应用")
    void apply_alreadyApplied_throws() {
        UUID runId = UUID.randomUUID();
        StandardPackageImportRun run = previewedRun(runId, "{}");
        run.setStatus("APPLIED");
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.apply(runId, "tester"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不可重复应用");
    }

    @Test
    @DisplayName("rollback：CREATE 删除、UPDATE 还原、缺失实体跳过，run 置 ROLLED_BACK")
    void rollback_deletesCreatesRestoresUpdatesSkipsMissing() throws Exception {
        UUID runId = UUID.randomUUID();
        StandardPackageImportRun run = previewedRun(runId, "{}");
        run.setStatus("APPLIED");
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        UUID createdTermId = UUID.randomUUID();
        StandardPackageImportRunItem createItem = new StandardPackageImportRunItem();
        createItem.setRunId(runId);
        createItem.setSeq(2);
        createItem.setEntityType("TERM");
        createItem.setEntityId(createdTermId.toString());
        createItem.setAction("CREATE");

        UUID updatedTermId = UUID.randomUUID();
        StandardPackageImportRunItem updateItem = new StandardPackageImportRunItem();
        updateItem.setRunId(runId);
        updateItem.setSeq(1);
        updateItem.setEntityType("TERM");
        updateItem.setEntityId(updatedTermId.toString());
        updateItem.setAction("UPDATE");
        updateItem.setBeforeJson(objectMapper.writeValueAsString(Map.of("code", "OLD", "name", "旧名称", "status", "ACTIVE")));

        StandardPackageImportRunItem missingItem = new StandardPackageImportRunItem();
        missingItem.setRunId(runId);
        missingItem.setSeq(3);
        missingItem.setEntityType("CODE_VALUE");
        missingItem.setEntityId("999");
        missingItem.setAction("CREATE");

        when(runItemRepository.findByRunIdOrderBySeqDesc(runId)).thenReturn(List.of(missingItem, createItem, updateItem));
        when(codeValueRepository.existsById(999L)).thenReturn(false);
        when(glossaryTermRepository.existsById(createdTermId)).thenReturn(true);
        ModelingGlossaryTerm existingTerm = new ModelingGlossaryTerm();
        existingTerm.setId(updatedTermId);
        existingTerm.setCode("NEW");
        existingTerm.setName("新名称");
        when(glossaryTermRepository.findById(updatedTermId)).thenReturn(Optional.of(existingTerm));
        lenient().when(glossaryTermRepository.save(any(ModelingGlossaryTerm.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.rollback(runId, "tester");

        assertThat(result.get("deleted")).isEqualTo(1);
        assertThat(result.get("restored")).isEqualTo(1);
        assertThat(String.valueOf(result.get("skipped"))).contains("999");
        verify(glossaryTermRepository).deleteById(createdTermId);
        assertThat(existingTerm.getCode()).isEqualTo("OLD");
        assertThat(existingTerm.getName()).isEqualTo("旧名称");

        ArgumentCaptor<StandardPackageImportRun> runCaptor = ArgumentCaptor.forClass(StandardPackageImportRun.class);
        verify(runRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo("ROLLED_BACK");
    }

    @Test
    @DisplayName("rollback：已回滚的 run 幂等返回，不报错")
    void rollback_alreadyRolledBack_isIdempotent() {
        UUID runId = UUID.randomUUID();
        StandardPackageImportRun run = previewedRun(runId, "{}");
        run.setStatus("ROLLED_BACK");
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        Map<String, Object> result = service.rollback(runId, "tester");

        assertThat(result.get("status")).isEqualTo("ROLLED_BACK");
        assertThat(String.valueOf(result.get("message"))).contains("已回滚");
        verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("apply：数据元已存在时走更新并记录 before-image")
    void apply_existingElement_recordsUpdateImage() {
        UUID runId = UUID.randomUUID();
        String payload =
            "{\"elements\":[{\"fieldNameCn\":\"性别\",\"fieldNameEn\":\"gender\",\"dataType\":\"VARCHAR\",\"dataLength\":1," +
            "\"nullable\":false,\"domain\":\"人口\",\"description\":\"d\",\"sourceSystem\":\"MDM\",\"securityLevel\":\"INTERNAL\"}]}";
        when(runRepository.findById(runId)).thenReturn(Optional.of(previewedRun(runId, payload)));

        com.yuzhi.dts.platform.domain.modeling.MetadataStandard existing = new com.yuzhi.dts.platform.domain.modeling.MetadataStandard();
        UUID elementId = UUID.randomUUID();
        existing.setId(elementId);
        existing.setFieldNameCn("旧性别");
        existing.setFieldNameEn("gender");
        existing.setDomain("人口");
        when(metadataStandardRepository.findByFieldNameEnIgnoreCaseAndDomainIgnoreCase("gender", "人口")).thenReturn(Optional.of(existing));
        when(metadataStandardService.update(any(UUID.class), any(MetadataStandardUpsertRequest.class))).thenReturn(new MetadataStandardDto());

        Map<String, Object> result = service.apply(runId, "tester");

        assertThat(result.get("totalUpdated")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StandardPackageImportRunItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(runItemRepository).saveAll(captor.capture());
        StandardPackageImportRunItem item = captor.getValue().get(0);
        assertThat(item.getAction()).isEqualTo("UPDATE");
        assertThat(item.getEntityId()).isEqualTo(elementId.toString());
        assertThat(item.getBeforeJson()).contains("旧性别");
        verify(metadataStandardService).update(any(UUID.class), any(MetadataStandardUpsertRequest.class));
    }
}
