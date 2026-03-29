package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ModelingSqlModelVisibilityTest {

    @Mock private ModelingSqlModelRepository repo;
    @Mock private ModelingPlanRepository planRepo;
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
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    private ModelingSqlModelService service;
    private List<ModelingSqlModel> storedModels;

    private final PlatformTransactionManager txManager = new PlatformTransactionManager() {
        @Override public TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition def) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) {}
        @Override public void rollback(TransactionStatus status) {}
    };

    @BeforeEach
    void setUp() {
        storedModels = new ArrayList<>();
        service = new ModelingSqlModelService(
            repo, planRepo, dataSourceRepository, adminInfraClient,
            organizationVisibilityService, security, dbtConfigService,
            datasetRepository, tableRepository, columnRepository,
            queryDatasetAssetRepository, biReportLinkRepository,
            columnSyncService, auditService, objectMapper, fileService,
            taskExecutor, txManager
        );

        lenient().when(organizationVisibilityService.isRoot(anyString())).thenReturn(false);
        lenient().when(adminInfraClient.fetchDefaultDataLake()).thenReturn(Optional.empty());
        lenient().when(repo.findAll()).thenAnswer(inv -> new ArrayList<>(storedModels));
        lenient().when(repo.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return storedModels.stream().filter(m -> id.equals(m.getId())).findFirst();
        });
    }

    // ── list visibility ──────────────────────────────────────────────

    @Test
    void list_shouldFilterOutModelsFromOtherDepartments() {
        when(security.resolveActiveDept(anyString())).thenReturn("D1");
        when(security.hasInstituteScope()).thenReturn(false);

        ModelingSqlModel ownModel = model("D1", "own_model");
        ModelingSqlModel otherModel = model("D2", "other_model");
        storedModels.add(ownModel);
        storedModels.add(otherModel);

        List<ModelingSqlModelService.SqlModelDto> result = service.list(null, null, "D1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("own_model");
    }

    @Test
    void list_shouldShowAllModelsForInstituteScope() {
        when(security.resolveActiveDept(anyString())).thenReturn("D1");
        when(security.hasInstituteScope()).thenReturn(true);

        storedModels.add(model("D1", "own_model"));
        storedModels.add(model("D2", "other_model"));
        storedModels.add(model("D3", "third_model"));

        List<ModelingSqlModelService.SqlModelDto> result = service.list(null, null, "D1");

        assertThat(result).hasSize(3);
    }

    @Test
    void list_shouldShowModelsWithNullOwnerDeptToEveryone() {
        when(security.resolveActiveDept(anyString())).thenReturn("D1");
        when(security.hasInstituteScope()).thenReturn(false);

        ModelingSqlModel unowned = new ModelingSqlModel();
        unowned.setId(UUID.randomUUID());
        unowned.setName("unowned_model");
        unowned.setOwnerDept(null);
        unowned.setSqlText("select 1");
        unowned.setEnabled(true);
        storedModels.add(unowned);

        List<ModelingSqlModelService.SqlModelDto> result = service.list(null, null, "D1");

        assertThat(result).hasSize(1);
    }

    @Test
    void list_shouldShowModelsWithRootOwnerDeptToEveryone() {
        when(security.resolveActiveDept(anyString())).thenReturn("D1");
        when(security.hasInstituteScope()).thenReturn(false);
        when(organizationVisibilityService.isRoot("ROOT")).thenReturn(true);

        ModelingSqlModel rootModel = model("ROOT", "root_model");
        storedModels.add(rootModel);

        List<ModelingSqlModelService.SqlModelDto> result = service.list(null, null, "D1");

        assertThat(result).hasSize(1);
    }

    // ── get visibility ───────────────────────────────────────────────

    @Test
    void get_shouldRejectAccessToCrossDepartmentModel() {
        when(security.resolveActiveDept(anyString())).thenReturn("D1");
        when(security.hasInstituteScope()).thenReturn(false);

        ModelingSqlModel otherModel = model("D2", "secret_model");
        storedModels.add(otherModel);

        assertThatThrownBy(() -> service.get(otherModel.getId(), "D1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void get_shouldAllowInstituteUserToAccessAnyModel() {
        when(security.resolveActiveDept(anyString())).thenReturn("D1");
        when(security.hasInstituteScope()).thenReturn(true);

        ModelingSqlModel otherModel = model("D2", "cross_dept_model");
        storedModels.add(otherModel);

        ModelingSqlModelService.SqlModelDto result = service.get(otherModel.getId(), "D1");

        assertThat(result.name()).isEqualTo("cross_dept_model");
    }

    @Test
    void get_shouldThrowNotFoundForNonExistentModel() {
        when(security.resolveActiveDept(anyString())).thenReturn("D1");
        when(security.hasInstituteScope()).thenReturn(true);

        assertThatThrownBy(() -> service.get(UUID.randomUUID(), "D1"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ── isOwnerDeptVisible unit checks ───────────────────────────────

    @Test
    void isOwnerDeptVisible_shouldReturnTrueWhenOwnerDeptIsNull() {
        assertThat(service.isOwnerDeptVisible(null, "D1", false)).isTrue();
    }

    @Test
    void isOwnerDeptVisible_shouldReturnTrueForInstituteScope() {
        assertThat(service.isOwnerDeptVisible("D2", "D1", true)).isTrue();
    }

    @Test
    void isOwnerDeptVisible_shouldReturnFalseWhenActiveDeptIsEmpty() {
        assertThat(service.isOwnerDeptVisible("D1", "", false)).isFalse();
    }

    @Test
    void isOwnerDeptVisible_shouldReturnTrueWhenDepartmentsMatch() {
        assertThat(service.isOwnerDeptVisible("D1", "D1", false)).isTrue();
    }

    @Test
    void isOwnerDeptVisible_shouldReturnFalseForMismatchedDepartments() {
        assertThat(service.isOwnerDeptVisible("D2", "D1", false)).isFalse();
    }

    // ── helper ───────────────────────────────────────────────────────

    private ModelingSqlModel model(String dept, String name) {
        ModelingSqlModel m = new ModelingSqlModel();
        m.setId(UUID.randomUUID());
        m.setName(name);
        m.setOwnerDept(dept);
        m.setSqlText("select 1");
        m.setEnabled(true);
        return m;
    }
}
