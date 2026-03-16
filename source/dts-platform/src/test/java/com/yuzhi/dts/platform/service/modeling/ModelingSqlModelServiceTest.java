package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelingSqlModelServiceTest {

    @Mock
    private ModelingSqlModelRepository repo;

    @Mock
    private ModelingPlanRepository planRepo;

    @Mock
    private InfraOdsTableMappingRepository odsTableMappingRepository;

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private OrganizationVisibilityService organizationVisibilityService;

    @Mock
    private DataStandardSecurity security;

    @Mock
    private DbtConfigService dbtConfigService;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogTableSchemaRepository tableRepository;

    @Mock
    private CatalogColumnSchemaRepository columnRepository;

    @Mock
    private CatalogColumnSyncService columnSyncService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ModelingSqlModelService service;

    @TempDir
    Path tempDir;

    private UUID planId;
    private ModelingPlan plan;
    private List<ModelingSqlModel> storedModels;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
        storedModels = new ArrayList<>();
        plan = new ModelingPlan();
        plan.setId(planId);
        plan.setName("Patent Plan");
        plan.setOwnerDept("D1");

        lenient().when(security.resolveActiveDept(anyString())).thenReturn("D1");
        lenient().when(security.hasInstituteScope()).thenReturn(false);
        lenient().when(organizationVisibilityService.isRoot(anyString())).thenReturn(false);
        lenient().when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
        lenient().when(columnSyncService.parseCsv(any(Path.class))).thenReturn(List.of());
        lenient().when(repo.findFirstByPlanIdAndNameIgnoreCase(any(UUID.class), anyString())).thenReturn(Optional.empty());
        lenient().when(repo.findAll()).thenAnswer(invocation -> new ArrayList<>(storedModels));
        lenient().when(repo.findByPlanId(any(UUID.class))).thenAnswer(invocation -> {
            UUID requestedPlanId = invocation.getArgument(0);
            return storedModels.stream().filter(model -> requestedPlanId.equals(model.getPlanId())).toList();
        });
        lenient().when(repo.save(any(ModelingSqlModel.class))).thenAnswer(invocation -> {
            ModelingSqlModel model = invocation.getArgument(0);
            if (model.getId() == null) {
                model.setId(UUID.randomUUID());
            }
            storedModels.removeIf(existing -> existing.getId() != null && existing.getId().equals(model.getId()));
            storedModels.add(model);
            return model;
        });
        lenient().when(
            datasetRepository.existsByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndWarehouseLayerIgnoreCaseAndEnabledTrue(
                anyString(),
                anyString(),
                eq("ODS")
            )
        ).thenReturn(true);

        DbtConfigService.DbtWorkspaceConfig cfg = new DbtConfigService.DbtWorkspaceConfig(
            true,
            tempDir.toString(),
            tempDir.toString(),
            "dts",
            "dev",
            null,
            null,
            "public",
            Map.of()
        );
        DbtConfigService.DbtConfigView view = new DbtConfigService.DbtConfigView(
            true,
            cfg,
            DbtConfigService.DbtProfileStatus.skipped("test"),
            null,
            new DbtConfigService.DbtWorkspaceStatus(true, "ok", Map.of())
        );
        when(dbtConfigService.loadConfig()).thenReturn(view);
    }

    @Test
    void generateFromOds_shouldUseDatasetSource_whenRequestAndMappingSourceUnavailable() {
        UUID mappingId = UUID.randomUUID();
        UUID badRequestSourceId = UUID.randomUUID();
        UUID badMappingSourceId = UUID.randomUUID();
        UUID datasetSourceId = UUID.randomUUID();

        InfraOdsTableMapping mapping = mapping(mappingId, badMappingSourceId, "ods", "ods_patent_info", "patent_info");
        when(odsTableMappingRepository.findAllById(anyCollection())).thenReturn(List.of(mapping));

        CatalogDataset dataset = new CatalogDataset();
        dataset.setSourceId(datasetSourceId);
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("ods", "ods_patent_info")).thenReturn(List.of(dataset));

        InfraDataSource datasetSource = source(datasetSourceId, "ODS-Lake", "postgres");
        Map<UUID, InfraDataSource> sourceMap = new LinkedHashMap<>();
        sourceMap.put(datasetSourceId, datasetSource);
        when(dataSourceRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(sourceMap.get(id));
        });
        when(dataSourceRepository.findByStatusIgnoreCase(anyString())).thenReturn(List.of());
        when(dataSourceRepository.findAll()).thenReturn(List.of());

        ModelingSqlModelService.SqlModelOdsGenerateRequest request = new ModelingSqlModelService.SqlModelOdsGenerateRequest(
            planId,
            badRequestSourceId,
            List.of(mappingId),
            "public",
            "table",
            "tag1",
            "D1",
            true,
            "DRAFT",
            true,
            false,
            false,
            true
        );

        ModelingSqlModelService.SqlModelOdsGenerateResult result = service.generateFromOds(request, "D1");

        assertThat(result.modelsCreated()).isEqualTo(1);
        assertThat(result.skipped()).isEmpty();

        ArgumentCaptor<ModelingSqlModel> captor = ArgumentCaptor.forClass(ModelingSqlModel.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getSourceDataSourceId()).isEqualTo(datasetSourceId);
    }

    @Test
    void generateFromOds_shouldSkipBrokenMappingAndContinueOtherMappings() {
        UUID mappingSkipId = UUID.randomUUID();
        UUID mappingOkId = UUID.randomUUID();
        UUID datasetSourceId = UUID.randomUUID();

        InfraOdsTableMapping skip = mapping(mappingSkipId, null, "ods", "ods_old", "old");
        InfraOdsTableMapping ok = mapping(mappingOkId, null, "ods", "ods_new", "new");
        when(odsTableMappingRepository.findAllById(anyCollection())).thenReturn(List.of(skip, ok));

        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("ods", "ods_old")).thenReturn(List.of());
        CatalogDataset okDataset = new CatalogDataset();
        okDataset.setSourceId(datasetSourceId);
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("ods", "ods_new")).thenReturn(List.of(okDataset));

        InfraDataSource datasetSource = source(datasetSourceId, "ODS-Lake", "postgres");
        when(dataSourceRepository.findByStatusIgnoreCase(anyString())).thenReturn(List.of());
        when(dataSourceRepository.findAll()).thenReturn(List.of());
        when(dataSourceRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (datasetSourceId.equals(id)) {
                return Optional.of(datasetSource);
            }
            return Optional.empty();
        });

        ModelingSqlModelService.SqlModelOdsGenerateRequest request = new ModelingSqlModelService.SqlModelOdsGenerateRequest(
            planId,
            null,
            List.of(mappingSkipId, mappingOkId),
            "public",
            "table",
            "tag1",
            "D1",
            true,
            "DRAFT",
            true,
            false,
            false,
            true
        );

        ModelingSqlModelService.SqlModelOdsGenerateResult result = service.generateFromOds(request, "D1");

        assertThat(result.mappingsTotal()).isEqualTo(2);
        assertThat(result.modelsCreated()).isEqualTo(1);
        assertThat(result.skipped()).anyMatch(msg -> msg.contains("ods.ods_old") && msg.contains("未找到可用来源数据源"));

        ArgumentCaptor<ModelingSqlModel> captor = ArgumentCaptor.forClass(ModelingSqlModel.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getSourceDataSourceId()).isEqualTo(datasetSourceId);
    }

    @Test
    void list_shouldDiscoverWorkspaceModelsFromDbtProject() throws Exception {
        Path modelsDir = tempDir.resolve("models").resolve("ads").resolve("project");
        Files.createDirectories(modelsDir);
        Files.writeString(modelsDir.resolve("ads_project_cockpit_summary.sql"), "select 1 as metric");

        UUID warehouseId = UUID.randomUUID();
        InfraDataSource warehouse = source(warehouseId, "数仓 (biadmin)", "postgres");
        warehouse.setStatus("ACTIVE");
        when(dataSourceRepository.findByStatusIgnoreCase(anyString())).thenReturn(List.of(warehouse));
        when(dataSourceRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        List<ModelingSqlModelService.SqlModelDto> models = service.list(null, "cockpit", "D1");

        assertThat(models).hasSize(1);
        ModelingSqlModelService.SqlModelDto dto = models.get(0);
        assertThat(dto.name()).isEqualTo("ads_project_cockpit_summary");
        assertThat(dto.layer()).isEqualTo("ADS");
        assertThat(dto.modelPath()).isEqualTo("models/ads/project/ads_project_cockpit_summary.sql");
        assertThat(dto.sql()).contains("select 1 as metric");
    }

    @Test
    void list_shouldDiscoverWorkspaceTagsAndDagSelectorFromSqlConfig() throws Exception {
        Path modelsDir = tempDir.resolve("models").resolve("ads").resolve("project");
        Files.createDirectories(modelsDir);
        Files.writeString(
            modelsDir.resolve("biz_ads_major_project_overview.sql"),
            """
            {{ config(materialized='table', tags=['project-management', 'biz', 'project-cockpit', 'ads']) }}
            select 1 as metric
            """
        );

        UUID warehouseId = UUID.randomUUID();
        InfraDataSource warehouse = source(warehouseId, "数仓 (biadmin)", "postgres");
        warehouse.setStatus("ACTIVE");
        when(dataSourceRepository.findByStatusIgnoreCase(anyString())).thenReturn(List.of(warehouse));
        when(dataSourceRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        List<ModelingSqlModelService.SqlModelDto> models = service.list(null, "major_project", "D1");

        assertThat(models).hasSize(1);
        ModelingSqlModelService.SqlModelDto dto = models.get(0);
        assertThat(dto.tags()).contains("project-management");
        assertThat(dto.tags()).contains("project-cockpit");
        assertThat(dto.dagSelector()).isEqualTo("tag:project-management");
    }

    @Test
    void list_shouldPreserveStoredDagSelector() {
        UUID warehouseId = UUID.randomUUID();
        InfraDataSource warehouse = source(warehouseId, "数仓 (biadmin)", "postgres");
        warehouse.setStatus("ACTIVE");
        when(dataSourceRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(UUID.randomUUID());
        model.setName("biz_ads_major_project_overview");
        model.setLayer("ADS");
        model.setSourceDataSourceId(warehouseId);
        model.setTags("project-management,biz,project-cockpit,ads");
        model.setDagSelector("tag:project-management");
        model.setSqlText("select 1");
        model.setEnabled(Boolean.TRUE);
        storedModels.add(model);

        List<ModelingSqlModelService.SqlModelDto> models = service.list(null, "major_project", "D1");

        assertThat(models).hasSize(1);
        assertThat(models.get(0).dagSelector()).isEqualTo("tag:project-management");
    }

    @Test
    void batchImportFromArchive_shouldReportValidationFailureWhenSqlFileMissing() throws Exception {
        UUID sourceId = UUID.randomUUID();

        Path archive = createArchive(
            "models.tsv",
            """
            name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path
            biz_ads_missing\tADS\tbiz_ads_missing.sql\t%s\t\tpublic\ttable\tproject-management\tDRAFT\ttrue\tD1\t\t
            """.formatted(sourceId)
        );

        ModelingSqlModelService.BatchImportResult result = service.batchImportFromArchive(
            planId,
            sourceId,
            false,
            archive,
            "D1"
        );

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.details()).singleElement().satisfies(detail -> {
            assertThat(detail.status()).isEqualTo("validation_failed");
            assertThat(detail.message()).contains("找不到 SQL 文件");
        });
    }

    @Test
    void batchImportFromArchive_shouldReportWriteFailureWhenWorkspaceFileCannotBeCreated() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId, "ODS-Lake", "postgres")));
        Files.createDirectories(tempDir.resolve("models").resolve("dwd"));
        Files.writeString(
            tempDir.resolve("models").resolve("dwd").resolve("patent_plan"),
            "block-directory-creation",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        Path archive = createArchive(
            "models.tsv",
            """
            name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path
            dwd_blocked_write\tDWD\tdwd_blocked_write.sql\t%s\t\tpublic\ttable\tproject-management\tDRAFT\ttrue\tD1\t\t
            """.formatted(sourceId),
            "dwd_blocked_write.sql",
            "select 1 as metric"
        );

        ModelingSqlModelService.BatchImportResult result = service.batchImportFromArchive(
            planId,
            sourceId,
            false,
            archive,
            "D1"
        );

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.details()).singleElement().satisfies(detail -> {
            assertThat(detail.status()).isEqualTo("write_failed");
            assertThat(detail.message()).isNotBlank();
        });
    }

    @Test
    void batchImportFromArchive_shouldFallbackToUsableDefaultSourceWhenProvidedSourceIsMissing() throws Exception {
        UUID missingSourceId = UUID.randomUUID();
        UUID fallbackSourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(missingSourceId)).thenReturn(Optional.empty());
        when(dataSourceRepository.findById(fallbackSourceId)).thenReturn(Optional.of(source(fallbackSourceId, "数仓 (biadmin)", "postgres")));
        when(dataSourceRepository.findByStatusIgnoreCase(anyString())).thenReturn(List.of(source(fallbackSourceId, "数仓 (biadmin)", "postgres")));

        Path archive = createArchive(
            "models.tsv",
            """
            name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path
            biz_dwd_project_node\tDWD\tbiz_dwd_project_node.sql\t\t\tpublic\ttable\tproject-management\tDRAFT\ttrue\tD1\t\t
            """,
            "biz_dwd_project_node.sql",
            "select 1 as metric"
        );

        ModelingSqlModelService.BatchImportResult result = service.batchImportFromArchive(
            planId,
            missingSourceId,
            false,
            archive,
            "D1"
        );

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(storedModels).singleElement().satisfies(model -> assertThat(model.getSourceDataSourceId()).isEqualTo(fallbackSourceId));
    }

    @Test
    void importFromFiles_shouldFailWhenWorkspaceSqlCannotBeWritten() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId, "ODS-Lake", "postgres")));
        Files.createDirectories(tempDir.resolve("models").resolve("ads"));
        Files.writeString(
            tempDir.resolve("models").resolve("ads").resolve("patent_plan"),
            "block-directory-creation",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        ModelingSqlModelService.SqlModelRequest request = new ModelingSqlModelService.SqlModelRequest(
            planId,
            "ads_blocked_write",
            null,
            "ADS",
            sourceId,
            "public",
            "table",
            "project-management",
            "desc",
            "select 1 as metric",
            true,
            "DRAFT",
            "D1",
            null
        );

        assertThatThrownBy(() -> service.importFromFiles(request, "select 1 as metric", null, "D1"))
            .isInstanceOf(IllegalStateException.class);
    }

    private InfraOdsTableMapping mapping(UUID id, UUID connectionId, String schema, String table, String entityCode) {
        InfraOdsTableMapping mapping = new InfraOdsTableMapping();
        mapping.setId(id);
        mapping.setConnectionId(connectionId);
        mapping.setOdsSchema(schema);
        mapping.setOdsTable(table);
        mapping.setEntityCode(entityCode);
        mapping.setEnabled(true);
        mapping.setOwnerDept("D1");
        mapping.setSystemCode("ERP");
        mapping.setBizCode("ERP");
        mapping.setStreamName(table);
        mapping.setStreamNamespace(schema);
        return mapping;
    }

    private InfraDataSource source(UUID id, String name, String type) {
        InfraDataSource source = new InfraDataSource();
        source.setId(id);
        source.setName(name);
        source.setType(type);
        source.setOwnerDept("D1");
        source.setStatus("ACTIVE");
        source.setProps(new ObjectMapper().createObjectNode().put("sourceSystem", "ERP").toString());
        return source;
    }

    private Path createArchive(String... files) throws Exception {
        Path archive = tempDir.resolve("batch-import-" + UUID.randomUUID() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int i = 0; i < files.length; i += 2) {
                zos.putNextEntry(new ZipEntry(files[i]));
                zos.write(files[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return archive;
    }
}
