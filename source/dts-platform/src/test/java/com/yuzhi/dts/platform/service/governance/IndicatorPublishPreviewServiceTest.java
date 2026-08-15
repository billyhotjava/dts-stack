package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovDimensionDictionaryRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorValidationResultDto;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

class IndicatorPublishPreviewServiceTest {

    private final GovIndicatorDefinitionRepository indicatorRepository = mock(GovIndicatorDefinitionRepository.class);
    private final GovIndicatorVersionRepository versionRepository = mock(GovIndicatorVersionRepository.class);
    private final GovIndicatorReferenceRepository referenceRepository = mock(GovIndicatorReferenceRepository.class);
    private final CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
    private final GovDimensionDictionaryRepository dimensionRepository = mock(GovDimensionDictionaryRepository.class);
    private final IndicatorService indicatorService = mock(IndicatorService.class);
    private final DbtIndicatorGenerator dbtGenerator = mock(DbtIndicatorGenerator.class);
    private final AccessChecker accessChecker = mock(AccessChecker.class);
    private final IndicatorPublishPreviewService service = new IndicatorPublishPreviewService(
        indicatorRepository,
        versionRepository,
        referenceRepository,
        datasetRepository,
        dimensionRepository,
        indicatorService,
        dbtGenerator,
        accessChecker,
        new ObjectMapper().findAndRegisterModules()
    );

    @BeforeEach
    void setUp() {
        when(indicatorService.resolveTrustedActiveDept("DEPT_A")).thenReturn("DEPT_A");
    }

    @Test
    void nullableDatasetReferenceAndDiffFieldsDoNotBreakPreview() {
        UUID indicatorId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomicIndicator(indicatorId, datasetId);
        IndicatorDto dto = indicatorDto(indicatorId);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        GovIndicatorReference incompleteReference = new GovIndicatorReference();
        incompleteReference.setIndicator(indicator);
        GovIndicatorVersion published = new GovIndicatorVersion();
        published.setIndicator(indicator);
        published.setVersion("v1");
        published.setStatus("PUBLISHED");
        published.setSnapshotJson("{\"category\":\"legacy\"}");

        when(indicatorService.get(indicatorId, "DEPT_A")).thenReturn(dto);
        when(indicatorRepository.findById(indicatorId)).thenReturn(Optional.of(indicator));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, "DEPT_A")).thenReturn(true);
        when(indicatorService.validateComputeRule(indicatorId, "DEPT_A")).thenReturn(successfulValidation());
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(indicator)).thenReturn(List.of(incompleteReference));
        when(versionRepository.findFirstByIndicatorAndStatusIgnoreCaseOrderByReleasedAtDescCreatedDateDesc(indicator, "PUBLISHED"))
            .thenReturn(Optional.of(published));
        when(dbtGenerator.previewSql(indicatorId)).thenReturn(Map.of("sql", "select 1"));

        Map<String, Object> payload = service.preview(indicatorId, "DEPT_A");

        assertThat(payload).containsEntry("readyToPublish", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> datasetPayload = (Map<String, Object>) payload.get("dataset");
        assertThat(datasetPayload).containsEntry("id", datasetId.toString()).containsEntry("name", null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> referenceChecks = (List<Map<String, Object>>) payload.get("referenceCheck");
        assertThat(referenceChecks.get(0)).containsEntry("refType", null).containsEntry("refTarget", null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> diffs = (List<Map<String, Object>>) payload.get("changesSinceLastPublish");
        assertThat(diffs)
            .anySatisfy(diff ->
                assertThat(diff).containsEntry("field", "category").containsEntry("before", "legacy").containsEntry("after", null)
            );
        verify(dbtGenerator).previewSql(indicatorId);
    }

    @Test
    void unauthorizedDatasetMetadataIsNotReturnedOrCompiled() {
        UUID indicatorId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomicIndicator(indicatorId, datasetId);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setName("sensitive_dataset");
        dataset.setOwnerDept("SECRET_DEPT");
        dataset.setClassification("DATA_SECRET");
        dataset.setHiveDatabase("secret_db");
        dataset.setHiveTable("secret_table");

        when(indicatorService.get(indicatorId, "DEPT_A")).thenReturn(indicatorDto(indicatorId));
        when(indicatorRepository.findById(indicatorId)).thenReturn(Optional.of(indicator));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(false);
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(indicator)).thenReturn(List.of());
        when(versionRepository.findFirstByIndicatorAndStatusIgnoreCaseOrderByReleasedAtDescCreatedDateDesc(indicator, "PUBLISHED"))
            .thenReturn(Optional.empty());

        Map<String, Object> payload = service.preview(indicatorId, "DEPT_A");

        assertThat(payload).containsEntry("readyToPublish", false).doesNotContainKey("dataset");
        assertThat(String.valueOf(payload))
            .contains("IND_DATASET_ACCESS_DENIED")
            .doesNotContain("sensitive_dataset", "SECRET_DEPT", "DATA_SECRET", "secret_db", "secret_table");
        verify(indicatorService, never()).validateComputeRule(any(UUID.class), any());
        verify(dbtGenerator, never()).previewSql(any(UUID.class));
    }

    @Test
    void compileFailureBecomesGenericBlockerWithoutSqlLeakage() {
        UUID indicatorId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        GovIndicatorDefinition indicator = atomicIndicator(indicatorId, datasetId);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);

        when(indicatorService.get(indicatorId, "DEPT_A")).thenReturn(indicatorDto(indicatorId));
        when(indicatorRepository.findById(indicatorId)).thenReturn(Optional.of(indicator));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, "DEPT_A")).thenReturn(true);
        when(indicatorService.validateComputeRule(indicatorId, "DEPT_A")).thenReturn(successfulValidation());
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(indicator)).thenReturn(List.of());
        when(versionRepository.findFirstByIndicatorAndStatusIgnoreCaseOrderByReleasedAtDescCreatedDateDesc(indicator, "PUBLISHED"))
            .thenReturn(Optional.empty());
        when(dbtGenerator.previewSql(indicatorId))
            .thenThrow(new IllegalArgumentException("select * from highly_sensitive_table"));

        Map<String, Object> payload = service.preview(indicatorId, "DEPT_A");

        assertThat(payload)
            .containsEntry("readyToPublish", false)
            .containsEntry("failureReasonCode", "IND_DBT_COMPILE_FAILED");
        assertThat(String.valueOf(payload)).doesNotContain("highly_sensitive_table", "select *");
    }

    @Test
    void derivedValidationAlsoRunsArtifactCompileGate() {
        UUID indicatorId = UUID.randomUUID();
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(indicatorId);
        indicator.setCode("DERIVED_METRIC");
        indicator.setName("Derived");
        indicator.setStatus("DRAFT");
        indicator.setIsDerived(true);
        indicator.setExpressionSql("{{ metric('BASE') }}");

        when(indicatorService.get(indicatorId, "DEPT_A")).thenReturn(indicatorDto(indicatorId));
        when(indicatorRepository.findById(indicatorId)).thenReturn(Optional.of(indicator));
        when(indicatorService.validateDerivation(indicatorId, "DEPT_A"))
            .thenReturn(new IndicatorDerivationValidationResult(true, "\"BASE\"", List.of(), List.of("BASE")));
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(indicator)).thenReturn(List.of());
        when(versionRepository.findFirstByIndicatorAndStatusIgnoreCaseOrderByReleasedAtDescCreatedDateDesc(indicator, "PUBLISHED"))
            .thenReturn(Optional.empty());
        when(dbtGenerator.previewSql(indicatorId)).thenReturn(Map.of("sql", "select 1"));

        Map<String, Object> payload = service.preview(indicatorId, "DEPT_A");

        assertThat(payload).containsEntry("readyToPublish", true);
        verify(dbtGenerator).previewSql(indicatorId);
    }

    @Test
    void pinnedDependencyVersionFailureIsVisibleInPublishPreview() {
        UUID indicatorId = UUID.randomUUID();
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(indicatorId);
        indicator.setCode("DERIVED_METRIC");
        indicator.setName("Derived");
        indicator.setStatus("DRAFT");
        indicator.setMetricType("DERIVED");
        indicator.setExpressionSql("{{metric:BASE}}");

        when(indicatorService.get(indicatorId, "DEPT_A")).thenReturn(indicatorDto(indicatorId));
        when(indicatorRepository.findById(indicatorId)).thenReturn(Optional.of(indicator));
        doThrow(
            new IndicatorConflictException(
                "INDICATOR_DEPENDENCY_VERSION_NOT_PUBLISHED: 上游指标 BASE 的固定版本 v2 尚未发布"
            )
        )
            .when(indicatorService)
            .validateDefinitionForPublish(indicator);
        when(indicatorService.validateDerivation(indicatorId, "DEPT_A"))
            .thenReturn(new IndicatorDerivationValidationResult(true, "\"BASE\"", List.of(), List.of("BASE")));
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(indicator)).thenReturn(List.of());
        when(versionRepository.findFirstByIndicatorAndStatusIgnoreCaseOrderByReleasedAtDescCreatedDateDesc(indicator, "PUBLISHED"))
            .thenReturn(Optional.empty());

        Map<String, Object> payload = service.preview(indicatorId, "DEPT_A");

        assertThat(payload)
            .containsEntry("readyToPublish", false)
            .containsEntry("failureReasonCode", "INDICATOR_DEPENDENCY_VERSION_NOT_PUBLISHED");
        verify(dbtGenerator, never()).previewSql(indicatorId);
    }

    @Test
    void expectedDefinitionBlockersDoNotMarkThePublishPreviewTransactionRollbackOnly() throws Exception {
        Transactional transaction = AnnotatedElementUtils.findMergedAnnotation(
            IndicatorService.class.getDeclaredMethod("validateDefinitionForPublish", GovIndicatorDefinition.class),
            Transactional.class
        );

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.noRollbackFor())
            .contains(IndicatorConflictException.class, IndicatorRequestException.class);
    }

    @Test
    void modelBoundAtomicDefinitionDoesNotRequireLegacyDatasetSqlOrDbtArtifact() {
        UUID indicatorId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(indicatorId);
        indicator.setCode("TASK_TOTAL");
        indicator.setName("任务总数");
        indicator.setStatus("DRAFT");
        indicator.setMetricType("ATOMIC");
        indicator.setIsDerived(false);
        indicator.setMeasureField("task_total");
        indicator.setAggregationType("SUM");
        indicator.setSourceRefs(
            "[{\"sourceType\":\"SEMANTIC_MODEL_REVISION\",\"sourceId\":\"" + modelId + "\",\"sourceVersion\":\"r2\"}]"
        );

        when(indicatorService.get(indicatorId, "DEPT_A")).thenReturn(indicatorDto(indicatorId));
        when(indicatorRepository.findById(indicatorId)).thenReturn(Optional.of(indicator));
        when(indicatorService.validateComputeRule(indicatorId, "DEPT_A")).thenReturn(successfulValidation());
        when(referenceRepository.findByIndicatorOrderByCreatedDateAsc(indicator)).thenReturn(List.of());
        when(versionRepository.findFirstByIndicatorAndStatusIgnoreCaseOrderByReleasedAtDescCreatedDateDesc(indicator, "PUBLISHED"))
            .thenReturn(Optional.empty());

        Map<String, Object> payload = service.preview(indicatorId, "DEPT_A");

        assertThat(payload).containsEntry("readyToPublish", true).containsEntry("datasetRequired", false);
        verify(indicatorService).validateComputeRule(indicatorId, "DEPT_A");
        verify(dbtGenerator, never()).previewSql(indicatorId);
    }

    private static GovIndicatorDefinition atomicIndicator(UUID indicatorId, UUID datasetId) {
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(indicatorId);
        indicator.setCode("GMV");
        indicator.setName("GMV");
        indicator.setStatus("DRAFT");
        indicator.setDatasetId(datasetId.toString());
        indicator.setExpressionSql("select 1");
        return indicator;
    }

    private static IndicatorDto indicatorDto(UUID indicatorId) {
        IndicatorDto dto = new IndicatorDto();
        dto.setId(indicatorId);
        dto.setCode("GMV");
        dto.setName("GMV");
        return dto;
    }

    private static IndicatorValidationResultDto successfulValidation() {
        IndicatorValidationResultDto validation = new IndicatorValidationResultDto();
        validation.setStatus("SUCCESS");
        validation.setMessage("OK");
        return validation;
    }
}
