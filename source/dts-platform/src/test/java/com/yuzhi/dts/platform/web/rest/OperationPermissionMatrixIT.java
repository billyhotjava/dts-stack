package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.repository.catalog.CatalogClassificationMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLifecycleRequestRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogDbtLineageService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleControlService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationPropagationService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import com.yuzhi.dts.platform.service.catalog.CatalogLifecycleRequestService;
import com.yuzhi.dts.platform.service.catalog.CatalogMetadataService;
import com.yuzhi.dts.platform.service.catalog.request.LifecycleRequestCreateRequest;
import com.yuzhi.dts.platform.service.openmetadata.OpenMetadataService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.service.sql.SqlExecutionExportRateLimiter;
import com.yuzhi.dts.platform.service.sql.SqlResultStreamService;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogDatasetResource;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper;
import com.yuzhi.dts.platform.web.rest.sql.SqlIdeExecutionController;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationPermissionMatrixIT {

    @Test
    void catalogDatasetMutationsFailClosedForCreateImportUpdateAndDelete() {
        CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogResourceHelper helper = mock(CatalogResourceHelper.class);
        CatalogDatasetResource resource = resource(datasetRepository, accessChecker, helper);
        CatalogDataset existing = dataset("finance");
        when(datasetRepository.findById(existing.getId())).thenReturn(java.util.Optional.of(existing));

        CatalogDataset created = new CatalogDataset();
        created.setTrinoCatalog("finance");
        assertThatThrownBy(() -> resource.createDataset(created)).isInstanceOf(AccessDeniedException.class);
        verify(accessChecker).canPerform(created, AssetAction.CREATE);

        CatalogDataset imported = new CatalogDataset();
        imported.setTrinoCatalog("finance");
        assertThatThrownBy(() -> resource.importDatasets(List.of(imported)))
            .isInstanceOf(AccessDeniedException.class);
        verify(accessChecker).canPerform(imported, AssetAction.IMPORT);

        assertThatThrownBy(() -> resource.updateDataset(existing.getId(), new CatalogDataset()))
            .isInstanceOf(AccessDeniedException.class);
        verify(accessChecker).canPerform(existing, AssetAction.UPDATE);

        assertThatThrownBy(() -> resource.deleteDataset(existing.getId()))
            .isInstanceOf(AccessDeniedException.class);
        verify(accessChecker).canPerform(existing, AssetAction.DELETE);
    }

    @ParameterizedTest
    @CsvSource({ "ARCHIVE,ARCHIVE", "DISPOSE,DELETE", "EXTEND_RETENTION,UPDATE" })
    void lifecycleRequestsMapToTheProtocolActionAndFailClosed(String requestType, AssetAction expected) {
        CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
        CatalogLifecycleRequestRepository requestRepository = mock(CatalogLifecycleRequestRepository.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogDataset dataset = dataset("finance");
        when(datasetRepository.findById(dataset.getId())).thenReturn(java.util.Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(accessChecker.departmentAllowed(dataset, null)).thenReturn(true);

        CatalogLifecycleRequestService service = new CatalogLifecycleRequestService(
            datasetRepository,
            requestRepository,
            accessChecker,
            mock(OrganizationVisibilityService.class),
            mock(AuditService.class),
            mock(CatalogLifecycleControlService.class)
        );
        LifecycleRequestCreateRequest request = new LifecycleRequestCreateRequest();
        request.setDatasetId(dataset.getId());
        request.setRequestType(requestType);

        assertThatThrownBy(() -> service.submit(request, "alice", null))
            .isInstanceOf(AccessDeniedException.class);
        verify(accessChecker).canPerform(dataset, expected);
    }

    @ParameterizedTest
    @CsvSource({
        "CREATE,CREATE",
        "STORE,COPY",
        "ARCHIVE,ARCHIVE",
        "TRASH,DELETE",
        "RESTORE,COPY",
        "PERMANENT_DESTROY,DESTROY",
    })
    void governedLifecycleActionsUseTheSameRuntimeMatrix(String lifecycleAction, AssetAction expected) {
        CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogDataset dataset = dataset("finance");
        when(datasetRepository.findById(dataset.getId())).thenReturn(java.util.Optional.of(dataset));
        CatalogLifecycleControlService service = new CatalogLifecycleControlService(
            mock(JdbcTemplate.class),
            datasetRepository,
            mock(CatalogClassificationService.class),
            mock(CatalogClassificationPropagationService.class),
            List.of(),
            new ObjectMapper(),
            accessChecker
        );

        assertThatThrownBy(() ->
            service.submit(
                new CatalogLifecycleControlService.SubmitCommand(
                    dataset.getId(),
                    lifecycleAction,
                    "a".repeat(64),
                    null,
                    0,
                    "finance",
                    "test",
                    30
                ),
                "alice"
            )
        ).isInstanceOf(AccessDeniedException.class);
        verify(accessChecker).canPerform(dataset, expected);
    }

    @Test
    void sqlResultExportReauthorizesTheRecordedSourceDataset() {
        QueryExecutionRepository executionRepository = mock(QueryExecutionRepository.class);
        CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogDataset dataset = dataset("finance");
        UUID executionId = UUID.randomUUID();
        QueryExecution execution = new QueryExecution();
        execution.setId(executionId);
        execution.setDatasetId(dataset.getId());
        when(executionRepository.findById(executionId)).thenReturn(java.util.Optional.of(execution));
        when(datasetRepository.findById(dataset.getId())).thenReturn(java.util.Optional.of(dataset));
        SqlIdeExecutionController controller = new SqlIdeExecutionController(
            mock(SqlResultStreamService.class),
            mock(AuditService.class),
            mock(SqlExecutionExportRateLimiter.class),
            executionRepository,
            datasetRepository,
            accessChecker
        );

        assertThatThrownBy(() -> controller.export(executionId, "csv"))
            .isInstanceOf(AccessDeniedException.class);
        verify(accessChecker).canPerform(dataset, AssetAction.EXPORT);
    }

    private static CatalogDatasetResource resource(
        CatalogDatasetRepository datasetRepository,
        AccessChecker accessChecker,
        CatalogResourceHelper helper
    ) {
        return new CatalogDatasetResource(
            datasetRepository,
            mock(CatalogDomainVisibilityService.class),
            mock(CatalogMaskingRuleRepository.class),
            mock(CatalogClassificationMappingRepository.class),
            mock(CatalogTableSchemaRepository.class),
            mock(CatalogColumnSchemaRepository.class),
            mock(AuditService.class),
            mock(CatalogFeatureProperties.class),
            mock(OrganizationVisibilityService.class),
            mock(OpenMetadataService.class),
            mock(CatalogMetadataService.class),
            helper,
            mock(GovIndicatorDefinitionRepository.class),
            mock(CatalogDbtLineageService.class),
            accessChecker
        );
    }

    private static CatalogDataset dataset(String catalog) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setName("finance_orders");
        dataset.setTrinoCatalog(catalog);
        return dataset;
    }
}
