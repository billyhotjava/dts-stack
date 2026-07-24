package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.governance.DefaultLakeDatasetGuard;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private AdminInfraClient adminInfraClient;

    @Mock
    private OrganizationVisibilityService organizationVisibilityService;

    @Mock
    private DataStandardSecurity security;

    @Mock
    private DbtConfigService dbtConfigService;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDomainRepository catalogDomainRepository;

    @Mock
    private CatalogTableSchemaRepository tableRepository;

    @Mock
    private CatalogColumnSchemaRepository columnRepository;

    @Mock
    private CatalogColumnSyncService columnSyncService;

    @Mock
    private QueryDatasetAssetRepository queryDatasetAssetRepository;

    @Mock
    private BiReportLinkRepository biReportLinkRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private DefaultLakeDatasetGuard defaultLakeDatasetGuard;

    @Mock
    private ModelFileService fileService;

    @Mock
    private Executor taskExecutor;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private ModelingSqlModelService service;

    private ModelGenerationService generationService;

    private final PlatformTransactionManager transactionManager = new NoopTransactionManager();

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
        service =
            new ModelingSqlModelService(
                repo,
                planRepo,
                dataSourceRepository,
                adminInfraClient,
                organizationVisibilityService,
                security,
                dbtConfigService,
                datasetRepository,
                catalogDomainRepository,
                tableRepository,
                columnRepository,
                queryDatasetAssetRepository,
                biReportLinkRepository,
                columnSyncService,
                auditService,
                objectMapper,
                fileService,
                taskExecutor,
                transactionManager,
                null,
                null
            );
        generationService =
            new ModelGenerationService(
                service,
                fileService,
                repo,
                planRepo,
                odsTableMappingRepository,
                dataSourceRepository,
                adminInfraClient,
                security,
                dbtConfigService,
                datasetRepository,
                tableRepository,
                columnRepository,
                queryDatasetAssetRepository,
                biReportLinkRepository
            );

        lenient().when(security.resolveActiveDept(anyString())).thenReturn("D1");
        lenient().when(security.hasInstituteScope()).thenReturn(false);
        lenient().when(organizationVisibilityService.isRoot(anyString())).thenReturn(false);
        lenient().when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.empty());
        lenient().when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
        lenient().when(columnSyncService.parseCsv(any(Path.class))).thenReturn(List.of());
        lenient().when(repo.findFirstByPlanIdAndNameIgnoreCase(any(UUID.class), anyString())).thenAnswer(invocation -> {
            UUID requestedPlanId = invocation.getArgument(0);
            String requestedName = invocation.getArgument(1);
            return storedModels
                .stream()
                .filter(model -> requestedPlanId.equals(model.getPlanId()))
                .filter(model -> requestedName != null && requestedName.equalsIgnoreCase(model.getName()))
                .findFirst();
        });
        lenient().when(repo.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return storedModels.stream().filter(model -> id.equals(model.getId())).findFirst();
        });
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
        lenient().doAnswer(invocation -> {
            ModelingSqlModel model = invocation.getArgument(0);
            storedModels.removeIf(existing -> existing.getId() != null && existing.getId().equals(model.getId()));
            return null;
        }).when(repo).delete(any(ModelingSqlModel.class));
        lenient().when(repo.findByModelPathIn(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> modelPaths = invocation.getArgument(0);
            return storedModels
                .stream()
                .filter(model -> modelPaths != null && modelPaths.contains(model.getModelPath()))
                .toList();
        });
        lenient().when(queryDatasetAssetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()).thenReturn(List.of());
        lenient().when(biReportLinkRepository.findAll()).thenReturn(List.of());
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
        lenient().when(dbtConfigService.loadConfig()).thenReturn(view);
    }

    @Test
    void syncDraftColumns_shouldUsePlanDomainIdWhenCreatingCatalogDataset() {
        UUID domainId = UUID.randomUUID();
        CatalogDomain domain = new CatalogDomain();
        domain.setId(domainId);
        domain.setName("销售主题域");
        plan.setDomain("销售域文本");
        plan.setDomainId(domainId);

        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(UUID.randomUUID());
        model.setPlanId(planId);
        model.setName("dwd_sales_order");
        model.setSchemaName("dwd");
        model.setLayer("DWD");
        model.setOwnerDept("D1");
        model.setModelPath("models/dwd/dwd_sales_order.sql");

        when(catalogDomainRepository.findById(domainId)).thenReturn(Optional.of(domain));
        when(datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("dwd", "dwd_sales_order"))
            .thenReturn(Optional.empty());
        when(datasetRepository.save(any(CatalogDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tableRepository.findFirstByDatasetAndNameIgnoreCase(any(CatalogDataset.class), eq("dwd_sales_order")))
            .thenReturn(Optional.empty());
        when(tableRepository.save(any(CatalogTableSchema.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(columnSyncService.parseCsv(any(Path.class))).thenReturn(
            List.of(new CatalogColumnSyncService.ColumnSpec("id", "varchar", false, "ID", null, null, null, null))
        );
        when(columnSyncService.upsertColumns(any(CatalogTableSchema.class), any(), anyString())).thenReturn(1);

        service.syncDraftColumns(model);

        ArgumentCaptor<CatalogDataset> datasetCaptor = ArgumentCaptor.forClass(CatalogDataset.class);
        verify(datasetRepository).save(datasetCaptor.capture());
        assertThat(datasetCaptor.getValue().getDomain()).isEqualTo(domain);
    }

    @Test
    void saveStandardBindings_shouldPersistBindingsInSemanticContractAndGenerateDbtSchemaYml() {
        UUID modelId = UUID.randomUUID();
        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(modelId);
        model.setPlanId(planId);
        model.setName("dwd_order_detail");
        model.setSchemaName("dwd");
        model.setLayer("DWD");
        model.setOwnerDept("D1");
        model.setSourceDataSourceId(UUID.randomUUID());
        model.setModelPath("models/dwd/dwd_order_detail.sql");
        storedModels.add(model);

        var binding = new ModelingSqlModelService.SqlModelStandardBinding(
            "order_status",
            null,
            "STD_ORDER_STATUS",
            "订单状态",
            "v1.0",
            "varchar",
            false,
            "ORDER_STATUS",
            "INTERNAL",
            "manual",
            "active",
            null
        );

        ModelingSqlModelService.SqlModelStandardBindingResult result =
            service.saveStandardBindings(modelId, new ModelingSqlModelService.SqlModelStandardBindingRequest(List.of(binding)), "D1");

        assertThat(result.mappedColumns()).isEqualTo(1);
        assertThat(model.getSemanticContract()).contains("standardBindings");
        assertThat(model.getSemanticContract()).contains("STD_ORDER_STATUS");

        ModelingSqlModelService.SqlModelSchemaYmlResult yml = service.generateSchemaYml(modelId, "D1");

        assertThat(yml.schemaYml()).contains("name: dwd_order_detail");
        assertThat(yml.schemaYml()).contains("name: order_status");
        assertThat(yml.schemaYml()).contains("standardCode: STD_ORDER_STATUS");
        assertThat(yml.schemaYml()).contains("codeSet: ORDER_STATUS");
        verify(fileService).writeSchemaYmlFile("models/dwd/dwd_order_detail.sql", yml.schemaYml());
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

        ModelingSqlModelService.SqlModelOdsGenerateResult result = generationService.generateFromOds(request, "D1");

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

        ModelingSqlModelService.SqlModelOdsGenerateResult result = generationService.generateFromOds(request, "D1");

        assertThat(result.mappingsTotal()).isEqualTo(2);
        assertThat(result.modelsCreated()).isEqualTo(1);
        assertThat(result.skipped()).anyMatch(msg -> msg.contains("ods.ods_old") && msg.contains("未找到可用来源数据源"));

        ArgumentCaptor<ModelingSqlModel> captor = ArgumentCaptor.forClass(ModelingSqlModel.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getSourceDataSourceId()).isEqualTo(datasetSourceId);
    }

    @Test
    void syncWorkspaceModels_shouldDiscoverWorkspaceModelsFromDbtProject() throws Exception {
        Path modelsDir = tempDir.resolve("models").resolve("ads").resolve("project");
        Files.createDirectories(modelsDir);
        Files.writeString(modelsDir.resolve("ads_project_cockpit_summary.sql"), "select 1 as metric");

        UUID warehouseId = UUID.randomUUID();
        InfraDataSource warehouse = source(warehouseId, "数仓 (biadmin)", "postgres");
        warehouse.setStatus("ACTIVE");
        when(dataSourceRepository.findByStatusIgnoreCase(anyString())).thenReturn(List.of(warehouse));
        when(dataSourceRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        generationService.syncWorkspaceModels("D1");

        List<ModelingSqlModelService.SqlModelDto> models = service.list(null, "cockpit", "D1");

        assertThat(models).hasSize(1);
        ModelingSqlModelService.SqlModelDto dto = models.get(0);
        assertThat(dto.name()).isEqualTo("ads_project_cockpit_summary");
        assertThat(dto.layer()).isEqualTo("ADS");
        assertThat(dto.modelPath()).isEqualTo("models/ads/project/ads_project_cockpit_summary.sql");
        assertThat(dto.sql()).contains("select 1 as metric");
    }

    @Test
    void syncWorkspaceModels_shouldDiscoverWorkspaceTagsAndDagSelectorFromSqlConfig() throws Exception {
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

        generationService.syncWorkspaceModels("D1");

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

        ModelingSqlModelService.BatchImportResult result = generationService.batchImportFromArchive(
            planId,
            sourceId,
            false,
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

        ModelingSqlModelService.BatchImportResult result = generationService.batchImportFromArchive(
            planId,
            sourceId,
            false,
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
    void batchImportFromArchive_shouldCopyCompanionWorkspaceFiles() throws Exception {
        UUID sourceId = UUID.randomUUID();
        InfraDataSource lake = source(sourceId, "ODS-Lake", "postgres");
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(lake));

        Path archive = createArchive(
            "models.tsv",
            String.join(
                "\n",
                "name\tlayer\tsql_path\tsource_data_source_id",
                "biz_ads_project_demo\tADS\tbiz_ads_project_demo.sql\t"
            ),
            "biz_ads_project_demo.sql",
            "select 1 as demo_id",
            "models/project_management_sources.yml",
            "version: 2\nsources: []\n",
            "macros/test_helper.sql",
            "{% macro test_helper() %}select 1{% endmacro %}\n",
            "seeds/project_mapping.csv",
            "id,name\n1,demo\n"
        );

        ModelingSqlModelService.BatchImportResult result = generationService.batchImportFromArchive(planId, sourceId, false, false, archive, "D1");

        assertThat(result.imported()).isEqualTo(1);
        assertThat(tempDir.resolve("models/project_management_sources.yml")).exists();
        assertThat(tempDir.resolve("macros/test_helper.sql")).exists();
        assertThat(tempDir.resolve("seeds/project_mapping.csv")).exists();
    }

    void batchImportFromArchive_shouldUpsertExistingModelInsteadOfCreatingDuplicate() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId, "ODS-Lake", "postgres")));

        Path archive = createArchive(
            "models.tsv",
            """
            name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path
            dim_node_type\tDWD\tdim_node_type.sql\t%s\t\tproject-management\ttable\tproject-management\tPUBLISHED\ttrue\tD1\t第一版\t
            """.formatted(sourceId),
            "dim_node_type.sql",
            "select '一般节点' as label"
        );

        ModelingSqlModelService.BatchImportResult first = generationService.batchImportFromArchive(planId, sourceId, false, false, archive, "D1");
        ModelingSqlModelService.BatchImportResult second = generationService.batchImportFromArchive(planId, sourceId, false, false, archive, "D1");

        assertThat(first.imported()).isEqualTo(1);
        assertThat(second.imported()).isEqualTo(1);
        assertThat(storedModels).hasSize(1);
        assertThat(storedModels.get(0).getName()).isEqualTo("dim_node_type");
    }

    @Test
    void create_shouldRejectDuplicateModelNameWithinSamePlan() {
        UUID sourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId, "ODS-Lake", "postgres")));

        ModelingSqlModel existing = new ModelingSqlModel();
        existing.setId(UUID.randomUUID());
        existing.setPlanId(planId);
        existing.setName("dim_node_type");
        existing.setOwnerDept("D1");
        storedModels.add(existing);

        ModelingSqlModelService.SqlModelRequest request = new ModelingSqlModelService.SqlModelRequest(
            planId,
            "dim_node_type",
            null,
            "DWD",
            sourceId,
            "public",
            "table",
            "project-management",
            "desc",
            "select 1 as id",
            true,
            "DRAFT",
            "D1",
            null
        );

        assertThatThrownBy(() -> service.create(request, "D1")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("模型名已存在");
    }

    @Test
    void create_shouldAllowExplicitNonDefaultPlatformSourceWhenDefaultLakeGuardIsPresent() {
        UUID defaultSourceId = UUID.randomUUID();
        UUID projectSourceId = UUID.randomUUID();
        ModelingSqlModelService guardedService = serviceWithDefaultLakeGuard();
        lenient().when(defaultLakeDatasetGuard.currentDefaultLakeSourceId()).thenReturn(Optional.of(defaultSourceId));
        lenient().doThrow(new IllegalArgumentException("仅允许选择默认数据湖数据源"))
            .when(defaultLakeDatasetGuard)
            .requireDefaultLakeSource(eq(projectSourceId), anyString());
        when(dataSourceRepository.findById(projectSourceId)).thenReturn(Optional.of(source(projectSourceId, "项目湖仓", "postgres")));

        ModelingSqlModelService.SqlModelRequest request = new ModelingSqlModelService.SqlModelRequest(
            planId,
            "dwd_project_budget",
            null,
            "DWD",
            projectSourceId,
            "public",
            "table",
            "project-management",
            "desc",
            "select 1 as id",
            true,
            "DRAFT",
            "D1",
            null
        );

        assertThatCode(() -> guardedService.create(request, "D1")).doesNotThrowAnyException();
        assertThat(storedModels).singleElement().satisfies(model -> assertThat(model.getSourceDataSourceId()).isEqualTo(projectSourceId));
    }

    @Test
    void boundAdvancedWorkspacePreservesModelSpecIdentityAndRequiresNewRevisionAfterEvidence() {
        UUID sourceId = UUID.randomUUID();
        UUID modelSpecId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId, "ODS-Lake", "postgres")));
        ModelingSqlModelService.SqlModelRequest request = new ModelingSqlModelService.SqlModelRequest(
            planId,
            modelSpecId,
            "dwd_project_budget",
            null,
            "DWD",
            sourceId,
            "public",
            "table",
            "project-management",
            "desc",
            "select 1 as id",
            true,
            "DRAFT",
            "D1",
            null
        );

        ModelingSqlModelService.SqlModelDto created = service.create(request, "D1");

        assertThat(created.modelSpecId()).isEqualTo(modelSpecId);
        assertThat(storedModels).singleElement().satisfies(model -> assertThat(model.getModelSpecId()).isEqualTo(modelSpecId));
        when(repo.hasCurrentLifecycleEvidence(modelSpecId)).thenReturn(true);
        assertThatThrownBy(() -> service.update(created.id(), request, "D1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("implementation revision");
    }

    @Test
    void update_shouldRejectRenameToDuplicateModelNameWithinSamePlan() {
        UUID sourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId, "ODS-Lake", "postgres")));

        ModelingSqlModel existing = new ModelingSqlModel();
        existing.setId(UUID.randomUUID());
        existing.setPlanId(planId);
        existing.setName("dim_node_type");
        existing.setOwnerDept("D1");

        ModelingSqlModel editing = new ModelingSqlModel();
        editing.setId(UUID.randomUUID());
        editing.setPlanId(planId);
        editing.setName("dwd_project_node");
        editing.setLayer("DWD");
        editing.setSourceDataSourceId(sourceId);
        editing.setOwnerDept("D1");
        editing.setModelPath("models/dwd/project/dwd_project_node.sql");

        storedModels.add(existing);
        storedModels.add(editing);

        ModelingSqlModelService.SqlModelRequest request = new ModelingSqlModelService.SqlModelRequest(
            planId,
            "dim_node_type",
            null,
            "DWD",
            sourceId,
            "public",
            "table",
            "project-management",
            "desc",
            "select 1 as id",
            true,
            "DRAFT",
            "D1",
            null
        );

        assertThatThrownBy(() -> service.update(editing.getId(), request, "D1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("模型名已存在");
    }

    @Test
    void list_shouldReturnAllExistingModelsWithSamePlanAndName() {
        UUID sourceId = UUID.randomUUID();

        ModelingSqlModel older = new ModelingSqlModel();
        older.setId(UUID.randomUUID());
        older.setPlanId(planId);
        older.setName("dim_node_type");
        older.setLayer("DWD");
        older.setSourceDataSourceId(sourceId);
        older.setSqlText("select 'old' as version");
        older.setStatus("DRAFT");
        older.setEnabled(Boolean.TRUE);
        older.setOwnerDept("D1");
        older.setCreatedDate(Instant.parse("2026-03-20T00:00:00Z"));
        older.setLastModifiedDate(Instant.parse("2026-03-20T00:00:00Z"));

        ModelingSqlModel newer = new ModelingSqlModel();
        newer.setId(UUID.randomUUID());
        newer.setPlanId(planId);
        newer.setName("dim_node_type");
        newer.setLayer("DWD");
        newer.setSourceDataSourceId(sourceId);
        newer.setSqlText("select 'new' as version");
        newer.setStatus("PUBLISHED");
        newer.setEnabled(Boolean.TRUE);
        newer.setOwnerDept("D1");
        newer.setCreatedDate(Instant.parse("2026-03-21T00:00:00Z"));
        newer.setLastModifiedDate(Instant.parse("2026-03-21T00:00:00Z"));

        storedModels.add(older);
        storedModels.add(newer);

        List<ModelingSqlModelService.SqlModelDto> models = service.list(planId, null, "D1");

        assertThat(models).hasSize(2);
        assertThat(models).extracting(ModelingSqlModelService.SqlModelDto::id).containsExactlyInAnyOrder(older.getId(), newer.getId());
    }

    @Test
    void delete_shouldRetainSharedModelFileWhenAnotherRecordStillReferencesSamePath() throws Exception {
        Path sqlPath = tempDir.resolve("models/dwd/project_management/dim_node_type.sql");
        Files.createDirectories(sqlPath.getParent());
        Files.writeString(sqlPath, "select 1 as id\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        ModelingSqlModel keeper = new ModelingSqlModel();
        keeper.setId(UUID.randomUUID());
        keeper.setPlanId(planId);
        keeper.setName("dim_node_type");
        keeper.setOwnerDept("D1");
        keeper.setModelPath("models/dwd/project_management/dim_node_type.sql");

        ModelingSqlModel duplicate = new ModelingSqlModel();
        duplicate.setId(UUID.randomUUID());
        duplicate.setPlanId(planId);
        duplicate.setName("dim_node_type");
        duplicate.setOwnerDept("D1");
        duplicate.setModelPath("models/dwd/project_management/dim_node_type.sql");

        storedModels.add(keeper);
        storedModels.add(duplicate);

        service.delete(duplicate.getId(), "D1");

        assertThat(storedModels).singleElement().extracting(ModelingSqlModel::getId).isEqualTo(keeper.getId());
        assertThat(sqlPath).exists();
    }

    @Test
    void governancePreview_shouldFindDuplicateAndLegacyProgramModels() {
        UUID sourceId = UUID.randomUUID();

        ModelingSqlModel duplicateOld = new ModelingSqlModel();
        duplicateOld.setId(UUID.randomUUID());
        duplicateOld.setPlanId(planId);
        duplicateOld.setName("dim_node_type");
        duplicateOld.setLayer("DWD");
        duplicateOld.setSourceDataSourceId(sourceId);
        duplicateOld.setSqlText("select 'old' as version");
        duplicateOld.setTags("project-management,dim");
        duplicateOld.setStatus("DRAFT");
        duplicateOld.setEnabled(Boolean.TRUE);
        duplicateOld.setOwnerDept("D1");
        duplicateOld.setModelPath("models/dwd/prj1/dim_node_type.sql");
        duplicateOld.setCreatedDate(Instant.parse("2026-03-20T00:00:00Z"));
        duplicateOld.setLastModifiedDate(Instant.parse("2026-03-20T00:00:00Z"));

        ModelingSqlModel duplicateNew = new ModelingSqlModel();
        duplicateNew.setId(UUID.randomUUID());
        duplicateNew.setPlanId(planId);
        duplicateNew.setName("dim_node_type");
        duplicateNew.setLayer("DWD");
        duplicateNew.setSourceDataSourceId(sourceId);
        duplicateNew.setSqlText("select 'new' as version");
        duplicateNew.setTags("project-management,dim");
        duplicateNew.setStatus("PUBLISHED");
        duplicateNew.setEnabled(Boolean.TRUE);
        duplicateNew.setOwnerDept("D1");
        duplicateNew.setModelPath("models/dwd/prj1/dim_node_type.sql");
        duplicateNew.setCreatedDate(Instant.parse("2026-03-21T00:00:00Z"));
        duplicateNew.setLastModifiedDate(Instant.parse("2026-03-21T00:00:00Z"));

        ModelingSqlModel legacyProgramModel = new ModelingSqlModel();
        legacyProgramModel.setId(UUID.randomUUID());
        legacyProgramModel.setPlanId(planId);
        legacyProgramModel.setName("biz_ads_major_project_overview");
        legacyProgramModel.setLayer("ADS");
        legacyProgramModel.setSourceDataSourceId(sourceId);
        legacyProgramModel.setSqlText("select program_id, program_name from legacy_project_view");
        legacyProgramModel.setTags("project-management,ads");
        legacyProgramModel.setStatus("PUBLISHED");
        legacyProgramModel.setEnabled(Boolean.TRUE);
        legacyProgramModel.setOwnerDept("D1");
        legacyProgramModel.setModelPath("models/ads/prj1/biz_ads_major_project_overview.sql");

        ModelingSqlModel cleanModel = new ModelingSqlModel();
        cleanModel.setId(UUID.randomUUID());
        cleanModel.setPlanId(planId);
        cleanModel.setName("biz_ads_project_overview");
        cleanModel.setLayer("ADS");
        cleanModel.setSourceDataSourceId(sourceId);
        cleanModel.setSqlText("select major_project_id from latest_project_view");
        cleanModel.setTags("project-management,ads");
        cleanModel.setStatus("PUBLISHED");
        cleanModel.setEnabled(Boolean.TRUE);
        cleanModel.setOwnerDept("D1");
        cleanModel.setModelPath("models/ads/prj1/biz_ads_project_overview.sql");

        storedModels.add(duplicateOld);
        storedModels.add(duplicateNew);
        storedModels.add(legacyProgramModel);
        storedModels.add(cleanModel);

        ModelingSqlModelService.SqlModelGovernancePreviewResult preview = generationService.previewGovernance(
            new ModelingSqlModelService.SqlModelGovernancePreviewRequest(
                planId,
                List.of("duplicate-model", "preset:project-management-legacy-program"),
                List.of(),
                null,
                null,
                null,
                null
            ),
            "D1"
        );

        assertThat(preview.total()).isEqualTo(2);
        assertThat(preview.items())
            .extracting(ModelingSqlModelService.SqlModelGovernancePreviewItem::name)
            .containsExactlyInAnyOrder("dim_node_type", "biz_ads_major_project_overview");
        assertThat(preview.items())
            .filteredOn(item -> item.name().equals("dim_node_type"))
            .singleElement()
            .satisfies(item -> assertThat(item.ruleHits()).contains("duplicate-model"));
        assertThat(preview.items())
            .filteredOn(item -> item.name().equals("biz_ads_major_project_overview"))
            .singleElement()
            .satisfies(item -> assertThat(item.ruleHits()).contains("preset:project-management-legacy-program"));
    }

    @Test
    void executeGovernance_shouldDeleteSelectedModelsAndOnlyRemoveUnreferencedFiles() throws Exception {
        Path sharedSqlPath = tempDir.resolve("models/dwd/prj1/dim_node_type.sql");
        Files.createDirectories(sharedSqlPath.getParent());
        Files.writeString(sharedSqlPath, "select 1 as id\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Path uniqueSqlPath = tempDir.resolve("models/ads/prj1/biz_ads_major_project_overview.sql");
        Files.createDirectories(uniqueSqlPath.getParent());
        Files.writeString(uniqueSqlPath, "select program_id from legacy\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        ModelingSqlModel keeper = new ModelingSqlModel();
        keeper.setId(UUID.randomUUID());
        keeper.setPlanId(planId);
        keeper.setName("dim_node_type");
        keeper.setOwnerDept("D1");
        keeper.setModelPath("models/dwd/prj1/dim_node_type.sql");

        ModelingSqlModel duplicate = new ModelingSqlModel();
        duplicate.setId(UUID.randomUUID());
        duplicate.setPlanId(planId);
        duplicate.setName("dim_node_type");
        duplicate.setOwnerDept("D1");
        duplicate.setModelPath("models/dwd/prj1/dim_node_type.sql");

        ModelingSqlModel legacy = new ModelingSqlModel();
        legacy.setId(UUID.randomUUID());
        legacy.setPlanId(planId);
        legacy.setName("biz_ads_major_project_overview");
        legacy.setOwnerDept("D1");
        legacy.setModelPath("models/ads/prj1/biz_ads_major_project_overview.sql");

        storedModels.add(keeper);
        storedModels.add(duplicate);
        storedModels.add(legacy);

        ModelingSqlModelService.SqlModelGovernanceExecuteResult result = generationService.executeGovernance(
            new ModelingSqlModelService.SqlModelGovernanceExecuteRequest(List.of(duplicate.getId(), legacy.getId()), true),
            "D1"
        );

        assertThat(result.deleted()).isEqualTo(2);
        assertThat(storedModels)
            .singleElement()
            .extracting(ModelingSqlModel::getId)
            .isEqualTo(keeper.getId());
        assertThat(sharedSqlPath).exists();
        assertThat(uniqueSqlPath).doesNotExist();
    }

    @Test
    void executeGovernance_shouldExposeDeletedFailedAndSkippedItems() throws Exception {
        ModelingSqlModel deletable = new ModelingSqlModel();
        deletable.setId(UUID.randomUUID());
        deletable.setPlanId(planId);
        deletable.setName("biz_ads_ok");
        deletable.setOwnerDept("D1");
        deletable.setModelPath("models/ads/prj1/biz_ads_ok.sql");

        ModelingSqlModel protectedModel = new ModelingSqlModel();
        protectedModel.setId(UUID.randomUUID());
        protectedModel.setPlanId(planId);
        protectedModel.setName("biz_ads_protected");
        protectedModel.setOwnerDept("D2");
        protectedModel.setModelPath("models/ads/prj1/biz_ads_protected.sql");

        ModelingSqlModel failing = new ModelingSqlModel();
        failing.setId(UUID.randomUUID());
        failing.setPlanId(planId);
        failing.setName("biz_ads_failed");
        failing.setOwnerDept("D1");
        failing.setModelPath("models/ads/prj1/biz_ads_failed.sql");

        storedModels.add(deletable);
        storedModels.add(protectedModel);
        storedModels.add(failing);

        doAnswer(invocation -> {
            ModelingSqlModel model = invocation.getArgument(0);
            if (failing.getId().equals(model.getId())) {
                throw new RuntimeException("删除模型失败");
            }
            storedModels.removeIf(existing -> existing.getId() != null && existing.getId().equals(model.getId()));
            return null;
        }).when(repo).delete(any(ModelingSqlModel.class));

        ModelingSqlModelService.SqlModelGovernanceExecuteResult result = generationService.executeGovernance(
            new ModelingSqlModelService.SqlModelGovernanceExecuteRequest(
                List.of(deletable.getId(), protectedModel.getId(), failing.getId(), UUID.randomUUID()),
                false
            ),
            "D1"
        );

        assertThat(result.requested()).isEqualTo(4);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.items())
            .extracting(ModelingSqlModelService.SqlModelGovernanceExecuteItem::result)
            .containsExactlyInAnyOrder("DELETED", "FAILED", "SKIPPED", "SKIPPED");
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

        ModelingSqlModelService.BatchImportResult result = generationService.batchImportFromArchive(
            planId,
            missingSourceId,
            false,
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
    void batchImportFromArchive_shouldAcceptAdminManagedDefaultDataLakeAsSource() throws Exception {
        UUID adminLakeId = UUID.randomUUID();
        when(dataSourceRepository.findById(adminLakeId)).thenReturn(Optional.empty());
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake(adminLakeId)));

        Path archive = createArchive(
            "models.tsv",
            """
            name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path
            biz_dwd_project_node\tDWD\tbiz_dwd_project_node.sql\t\t\tpublic\ttable\tproject-management\tDRAFT\ttrue\tD1\t\t
            """,
            "biz_dwd_project_node.sql",
            "select 1 as metric"
        );

        ModelingSqlModelService.BatchImportResult result = generationService.batchImportFromArchive(
            planId,
            adminLakeId,
            false,
            false,
            archive,
            "D1"
        );

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(storedModels).singleElement().satisfies(model -> assertThat(model.getSourceDataSourceId()).isEqualTo(adminLakeId));
    }

    @Test
    void list_shouldResolveAdminManagedDefaultDataLakeSourceName() {
        UUID adminLakeId = UUID.randomUUID();
        when(dataSourceRepository.findById(adminLakeId)).thenReturn(Optional.empty());
        when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.of(adminLake(adminLakeId)));

        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(UUID.randomUUID());
        model.setName("biz_ads_major_project_overview");
        model.setLayer("ADS");
        model.setSourceDataSourceId(adminLakeId);
        model.setTags("project-management,biz,project-cockpit,ads");
        model.setDagSelector("tag:project-management");
        model.setSqlText("select 1");
        model.setEnabled(Boolean.TRUE);
        storedModels.add(model);

        List<ModelingSqlModelService.SqlModelDto> models = service.list(null, "major_project", "D1");

        assertThat(models).hasSize(1);
        assertThat(models.get(0).sourceDataSourceName()).isEqualTo("默认数据湖");
        assertThat(models.get(0).sourceSystem()).isEqualTo("admin-data-lake");
    }

    @Test
    void list_shouldNotDiscoverWorkspaceModelsImplicitly() throws Exception {
        Path sqlFile = tempDir.resolve("models").resolve("dwd").resolve("rogue_model.sql");
        Files.createDirectories(sqlFile.getParent());
        Files.writeString(
            sqlFile,
            """
            select 1 as id
            """,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        List<ModelingSqlModelService.SqlModelDto> models = service.list(null, null, "D1");

        assertThat(models).isEmpty();
        assertThat(storedModels).isEmpty();
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

        assertThatThrownBy(() -> generationService.importFromFiles(request, "select 1 as metric", null, "D1"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteShouldDeferFileCleanupUntilAfterCommit() {
        UUID modelId = UUID.randomUUID();
        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(modelId);
        model.setName("dwd_patent");
        model.setOwnerDept("D1");
        model.setModelPath("models/dwd/dwd_patent.sql");
        storedModels.add(model);
        when(fileService.canDeleteModelPathAfterRemoving(eq("models/dwd/dwd_patent.sql"), any())).thenReturn(true);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(modelId, "D1");

            verify(repo).delete(model);
            verify(fileService, never()).deleteFileIfChanged(anyString(), any());
            verify(taskExecutor, never()).execute(any(Runnable.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

            verify(taskExecutor).execute(runnableCaptor.capture());
            verify(fileService, never()).deleteFileIfChanged(anyString(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        runnableCaptor.getValue().run();

        verify(fileService).deleteFileIfChanged("models/dwd/dwd_patent.sql", null);
    }

    @Test
    void executeGovernanceShouldDeferFileCleanupUntilAfterCommit() {
        UUID modelId = UUID.randomUUID();
        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(modelId);
        model.setPlanId(planId);
        model.setName("biz_ads_patent");
        model.setOwnerDept("D1");
        model.setModelPath("models/ads/biz_ads_patent.sql");
        storedModels.add(model);
        when(fileService.canDeleteModelPathAfterRemoving("models/ads/biz_ads_patent.sql", Set.of(modelId))).thenReturn(true);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        TransactionSynchronizationManager.initSynchronization();
        try {
            ModelingSqlModelService.SqlModelGovernanceExecuteResult result = generationService.executeGovernance(
                new ModelingSqlModelService.SqlModelGovernanceExecuteRequest(List.of(modelId), true),
                "D1"
            );

            assertThat(result.deleted()).isEqualTo(1);
            verify(repo).delete(model);
            verify(fileService, never()).deleteFileIfChanged(anyString(), any());
            verify(taskExecutor, never()).execute(any(Runnable.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

            verify(taskExecutor).execute(runnableCaptor.capture());
            verify(fileService, never()).deleteFileIfChanged(anyString(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        runnableCaptor.getValue().run();

        verify(fileService).deleteFileIfChanged("models/ads/biz_ads_patent.sql", null);
    }

    @Test
    void deleteShouldNotBubbleWhenCleanupSubmissionFailsAfterCommit() {
        UUID modelId = UUID.randomUUID();
        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(modelId);
        model.setName("dwd_patent");
        model.setOwnerDept("D1");
        model.setModelPath("models/dwd/dwd_patent.sql");
        storedModels.add(model);
        when(fileService.canDeleteModelPathAfterRemoving("models/dwd/dwd_patent.sql", Set.of(modelId))).thenReturn(true);
        doThrow(new RejectedExecutionException("queue-full")).when(taskExecutor).execute(any(Runnable.class));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(modelId, "D1");

            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit()))
                .doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(fileService, never()).deleteFileIfChanged(anyString(), any());
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

    private ModelingSqlModelService serviceWithDefaultLakeGuard() {
        return new ModelingSqlModelService(
            repo,
            planRepo,
            dataSourceRepository,
            adminInfraClient,
            organizationVisibilityService,
            security,
            dbtConfigService,
            datasetRepository,
            catalogDomainRepository,
            tableRepository,
            columnRepository,
            queryDatasetAssetRepository,
            biReportLinkRepository,
            columnSyncService,
            auditService,
            objectMapper,
            fileService,
            taskExecutor,
            transactionManager,
            defaultLakeDatasetGuard,
            null
        );
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

    private AdminInfraClient.AdminDataLakeConfig adminLake(UUID id) {
        AdminInfraClient.AdminDataLakeConfig lake = new AdminInfraClient.AdminDataLakeConfig();
        setField(lake, "id", id);
        setField(lake, "name", "默认数据湖");
        setField(lake, "type", "DATA_LAKE");
        setField(lake, "jdbcUrl", "jdbc:postgresql://localhost:5432/biadmin");
        setField(lake, "username", "biadmin");
        setField(lake, "password", "secret");
        setField(lake, "status", "ACTIVE");
        setField(lake, "defaulted", Boolean.TRUE);
        setField(lake, "lastVerifiedAt", Instant.now());
        return lake;
    }

    private void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set field " + name, ex);
        }
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

    private static final class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {}

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {}
    }
}
