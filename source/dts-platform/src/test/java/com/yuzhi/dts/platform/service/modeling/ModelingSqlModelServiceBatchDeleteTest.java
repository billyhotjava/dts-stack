package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelingSqlModelServiceBatchDeleteTest {

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

    @InjectMocks private ModelingSqlModelService service;

    @TempDir Path tempDir;

    private final List<ModelingSqlModel> storedModels = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(security.resolveActiveDept(anyString())).thenReturn("D1");
        lenient().when(security.hasInstituteScope()).thenReturn(false);
        lenient().when(organizationVisibilityService.isRoot(anyString())).thenReturn(false);
        lenient().when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.empty());
        lenient().when(queryDatasetAssetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()).thenReturn(List.of());
        lenient().when(biReportLinkRepository.findAll()).thenReturn(List.of());
        lenient().when(repo.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return storedModels.stream().filter(model -> id.equals(model.getId())).findFirst();
        });
        lenient()
            .doAnswer(invocation -> {
                ModelingSqlModel model = invocation.getArgument(0);
                storedModels.removeIf(existing -> existing.getId() != null && existing.getId().equals(model.getId()));
                return null;
            })
            .when(repo)
            .delete(any(ModelingSqlModel.class));
        lenient().when(fileService.canDeleteModelPathAfterRemoving(anyString(), any())).thenReturn(true);

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
    void deleteBatch_shouldDeleteAllRequestedModels_andReturnSummary() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        storedModels.add(model(id1, "m1", "models/dwd/m1.sql"));
        storedModels.add(model(id2, "m2", "models/dwd/m2.sql"));

        ModelingSqlModelService.BatchDeleteResult result = service.deleteBatch(
            new ModelingSqlModelService.BatchDeleteRequest(List.of(id1, id2)),
            "D1"
        );

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.failures()).isEmpty();
        assertThat(storedModels).isEmpty();
    }

    @Test
    void deleteBatch_shouldKeepGoing_whenSingleModelMissing() {
        UUID id1 = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        storedModels.add(model(id1, "m1", "models/dwd/m1.sql"));

        ModelingSqlModelService.BatchDeleteResult result = service.deleteBatch(
            new ModelingSqlModelService.BatchDeleteRequest(List.of(id1, missing)),
            "D1"
        );

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).modelId()).isEqualTo(missing);
        assertThat(storedModels).isEmpty();
    }

    private ModelingSqlModel model(UUID id, String name, String path) {
        ModelingSqlModel model = new ModelingSqlModel();
        model.setId(id);
        model.setName(name);
        model.setModelPath(path);
        model.setOwnerDept("D1");
        model.setPlanId(UUID.randomUUID());
        return model;
    }
}
