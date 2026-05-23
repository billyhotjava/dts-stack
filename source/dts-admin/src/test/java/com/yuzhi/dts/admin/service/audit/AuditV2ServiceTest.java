package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.repository.audit.AuditEntryRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditV2ServiceTest {

    private AuditEntryRepository repository;
    private AuditButtonRegistry buttonRegistry;
    private AuditActionCatalogService actionCatalogService;
    private AuditV2Service service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEntryRepository.class);
        buttonRegistry = mock(AuditButtonRegistry.class);
        actionCatalogService = mock(AuditActionCatalogService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditRecorder recorder = new AuditRecorder(repository, null, objectMapper);
        service = new AuditV2Service(
            recorder,
            buttonRegistry,
            mock(ChangeSnapshotFormatter.class),
            objectMapper,
            actionCatalogService
        );
        when(repository.save(any(AuditEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsPlatformSourceSystemAndDbCatalogMetadata() {
        when(actionCatalogService.resolve("platform", "CATALOG_DOMAIN_CREATE"))
            .thenReturn(
                Optional.of(
                    new AuditActionCatalogService.ResolvedAction(
                        "platform",
                        "catalog.domain",
                        "主题域",
                        "CATALOG_DOMAIN_CREATE",
                        "新增主题域",
                        AuditOperationKind.CREATE,
                        "catalog_domain",
                        false
                    )
                )
            );

        AuditActionRequest request = AuditActionRequest
            .builder("xiezm", "CATALOG_DOMAIN_CREATE")
            .sourceSystem("platform")
            .moduleOverride("catalog.assets", "数据资产")
            .operationOverride("CATALOG_ASSET_EDIT", "编辑数据资产", AuditOperationKind.UPDATE)
            .summary("新增主题域")
            .target("catalog_domain", 101L, "合同域")
            .build();

        service.record(request);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(captor.capture());
        AuditEntry saved = captor.getValue();
        assertThat(saved.getSourceSystem()).isEqualTo("platform");
        assertThat(saved.getModuleKey()).isEqualTo("catalog.domain");
        assertThat(saved.getModuleName()).isEqualTo("主题域");
        assertThat(saved.getOperationCode()).isEqualTo("CATALOG_DOMAIN_CREATE");
        assertThat(saved.getOperationName()).isEqualTo("新增主题域");
        assertThat(saved.getOperationKind()).isEqualTo(AuditOperationKind.CREATE.code());
    }

    @Test
    void recordsClassificationMissForUnknownPlatformActionInsteadOfTrustingOverrides() {
        when(actionCatalogService.resolve("platform", "PLATFORM_NEW_MODULE_SAVE")).thenReturn(Optional.empty());

        AuditActionRequest request = AuditActionRequest
            .builder("xiezm", "PLATFORM_NEW_MODULE_SAVE")
            .sourceSystem("platform")
            .moduleOverride("catalog.assets", "数据资产")
            .operationOverride("CATALOG_ASSET_EDIT", "编辑数据资产", AuditOperationKind.UPDATE)
            .summary("保存新模块配置")
            .allowEmptyTargets()
            .build();

        service.record(request);

        verify(actionCatalogService).recordMiss(eq(request), eq("NO_CATALOG_MATCH"));
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(captor.capture());
        AuditEntry saved = captor.getValue();
        assertThat(saved.getSourceSystem()).isEqualTo("platform");
        assertThat(saved.getModuleKey()).isEqualTo("platform.unclassified");
        assertThat(saved.getModuleName()).isEqualTo("未分类业务操作");
        assertThat(saved.getOperationCode()).isEqualTo("PLATFORM_NEW_MODULE_SAVE");
        assertThat(saved.getOperationName()).isEqualTo("保存新模块配置");
        assertThat(saved.getMetadata()).containsEntry("classificationReason", "NO_CATALOG_MATCH");
    }
}
