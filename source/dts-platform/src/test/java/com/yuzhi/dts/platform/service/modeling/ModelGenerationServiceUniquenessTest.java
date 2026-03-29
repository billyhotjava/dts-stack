package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ModelGenerationServiceUniquenessTest {

    @Mock private ModelingSqlModelRepository repo;
    @Mock private ModelingPlanRepository planRepo;
    @Mock private InfraOdsTableMappingRepository odsTableMappingRepository;
    @Mock private InfraDataSourceRepository dataSourceRepository;
    @Mock private AdminInfraClient adminInfraClient;
    @Mock private OrganizationVisibilityService organizationVisibilityService;
    @Mock private DataStandardSecurity security;
    @Mock private DbtConfigService dbtConfigService;
    @Mock private CatalogDatasetRepository datasetRepository;
    @Mock private CatalogTableSchemaRepository tableRepository;
    @Mock private CatalogColumnSchemaRepository columnRepository;
    @Mock private CatalogColumnSyncService columnSyncService;
    @Mock private QueryDatasetAssetRepository queryDatasetAssetRepository;
    @Mock private BiReportLinkRepository biReportLinkRepository;
    @Mock private AuditService auditService;
    @Mock private ModelFileService fileService;
    @Mock private Executor taskExecutor;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @TempDir Path tempDir;

    private final PlatformTransactionManager transactionManager = new NoopTransactionManager();
    private final List<ModelingSqlModel> storedModels = new ArrayList<>();

    private UUID planId;
    private ModelingSqlModelService coreService;
    private ModelGenerationService generationService;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
        ModelingPlan plan = new ModelingPlan();
        plan.setId(planId);
        plan.setName("Patent Plan");
        plan.setOwnerDept("D1");

        coreService =
            new ModelingSqlModelService(
                repo,
                planRepo,
                dataSourceRepository,
                adminInfraClient,
                organizationVisibilityService,
                security,
                dbtConfigService,
                datasetRepository,
                tableRepository,
                columnRepository,
                queryDatasetAssetRepository,
                biReportLinkRepository,
                columnSyncService,
                auditService,
                objectMapper,
                fileService,
                taskExecutor,
                transactionManager
            );
        generationService =
            new ModelGenerationService(
                coreService,
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
        lenient().when(queryDatasetAssetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()).thenReturn(List.of());
        lenient().when(biReportLinkRepository.findAll()).thenReturn(List.of());
        lenient().when(datasetRepository.existsByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndWarehouseLayerIgnoreCaseAndEnabledTrue(anyString(), anyString(), anyString())).thenReturn(true);
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

        DbtConfigService.DbtWorkspaceConfig cfg = new DbtConfigService.DbtWorkspaceConfig(
            true,
            tempDir.toString(),
            tempDir.toString(),
            "dts",
            "dev",
            null,
            null,
            "public",
            java.util.Map.of()
        );
        DbtConfigService.DbtConfigView view = new DbtConfigService.DbtConfigView(
            true,
            cfg,
            DbtConfigService.DbtProfileStatus.skipped("test"),
            null,
            new DbtConfigService.DbtWorkspaceStatus(true, "ok", java.util.Map.of())
        );
        lenient().when(dbtConfigService.loadConfig()).thenReturn(view);
    }

    @Test
    void importFromFiles_shouldRejectDuplicateModelNameWithinSamePlan() {
        UUID sourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId)));

        storedModels.add(existingModel("dwd_project_node"));

        ModelingSqlModelService.SqlModelRequest request = new ModelingSqlModelService.SqlModelRequest(
            planId,
            "dwd_project_node",
            null,
            "DWD",
            sourceId,
            "public",
            "table",
            "project-management",
            "desc",
            null,
            true,
            "DRAFT",
            "D1",
            null
        );

        assertThatThrownBy(() -> generationService.importFromFiles(request, "select 1 as id", null, "D1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("模型名已存在");
    }

    @Test
    void upsertImportFromFiles_shouldUpdateExistingModelWithoutCreatingDuplicate() {
        UUID sourceId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId)));

        ModelingSqlModel existing = existingModel("dwd_project_node");
        existing.setSqlText("select 'old' as version");
        storedModels.add(existing);

        ModelingSqlModelService.SqlModelRequest request = new ModelingSqlModelService.SqlModelRequest(
            planId,
            "dwd_project_node",
            null,
            "DWD",
            sourceId,
            "public",
            "table",
            "project-management",
            "desc",
            null,
            true,
            "DRAFT",
            "D1",
            null
        );

        ModelingSqlModelService.SqlModelDto dto = generationService.upsertImportFromFiles(request, "select 'new' as version", null, "D1");

        assertThat(dto.id()).isEqualTo(existing.getId());
        assertThat(storedModels).hasSize(1);
        assertThat(storedModels.get(0).getSqlText()).isEqualTo("select 'new' as version");
    }

    @Test
    void generateFromOds_shouldUpdateExistingModelInsteadOfCreatingDuplicate() {
        UUID sourceId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source(sourceId)));
        when(dataSourceRepository.findByStatusIgnoreCase(anyString())).thenReturn(List.of(source(sourceId)));
        when(odsTableMappingRepository.findAllById(anyCollection())).thenReturn(List.of(mapping(mappingId, sourceId, "ods", "ods_patent_info", "patent_info")));

        ModelingSqlModel existing = existingModel("dwd_patent_info");
        existing.setLayer("DWD");
        existing.setSourceDataSourceId(sourceId);
        existing.setSqlText("select 'old' as version");
        storedModels.add(existing);

        ModelingSqlModelService.SqlModelOdsGenerateRequest request = new ModelingSqlModelService.SqlModelOdsGenerateRequest(
            planId,
            sourceId,
            List.of(mappingId),
            "public",
            "table",
            "project-management",
            "D1",
            true,
            "DRAFT",
            true,
            false,
            false,
            true
        );

        ModelingSqlModelService.SqlModelOdsGenerateResult result = generationService.generateFromOds(request, "D1");

        assertThat(result.modelsCreated()).isZero();
        assertThat(result.modelsUpdated()).isEqualTo(1);
        assertThat(result.updatedModels()).containsExactly("dwd_patent_info");
        assertThat(storedModels).hasSize(1);
        assertThat(storedModels.get(0).getId()).isEqualTo(existing.getId());
    }

    private ModelingSqlModel existingModel(String name) {
        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(UUID.randomUUID());
        model.setPlanId(planId);
        model.setName(name);
        model.setOwnerDept("D1");
        model.setEnabled(Boolean.TRUE);
        model.setStatus("DRAFT");
        model.setCreatedDate(Instant.parse("2026-03-28T00:00:00Z"));
        model.setLastModifiedDate(Instant.parse("2026-03-28T00:00:00Z"));
        return model;
    }

    private InfraDataSource source(UUID id) {
        InfraDataSource source = new InfraDataSource();
        source.setId(id);
        source.setName("ODS-Lake");
        source.setType("postgres");
        source.setOwnerDept("D1");
        source.setStatus("ACTIVE");
        source.setProps(new ObjectMapper().createObjectNode().put("sourceSystem", "ERP").toString());
        return source;
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

    private static final class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {}

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {}
    }
}
