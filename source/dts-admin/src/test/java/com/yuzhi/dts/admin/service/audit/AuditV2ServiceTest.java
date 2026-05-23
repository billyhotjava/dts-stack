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
    void recordsAnalyticsSourceSystemAndDbCatalogMetadata() {
        when(actionCatalogService.resolve("analytics", "SCREEN_UPDATE"))
            .thenReturn(
                Optional.of(
                    new AuditActionCatalogService.ResolvedAction(
                        "analytics",
                        "analytics.screen",
                        "数据大屏",
                        "SCREEN_UPDATE",
                        "修改大屏",
                        AuditOperationKind.UPDATE,
                        "SCREEN",
                        false
                    )
                )
            );

        AuditActionRequest request = AuditActionRequest
            .builder("xiezm", "SCREEN_UPDATE")
            .sourceSystem("analytics")
            .moduleOverride("analytics.general", "分析服务")
            .operationOverride("ANALYTICS_GENERIC_EVENT", "通用分析事件", AuditOperationKind.OTHER)
            .summary("修改大屏")
            .target("SCREEN", 42L, "项目执行监控")
            .build();

        service.record(request);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(captor.capture());
        AuditEntry saved = captor.getValue();
        assertThat(saved.getSourceSystem()).isEqualTo("analytics");
        assertThat(saved.getModuleKey()).isEqualTo("analytics.screen");
        assertThat(saved.getModuleName()).isEqualTo("数据大屏");
        assertThat(saved.getOperationCode()).isEqualTo("SCREEN_UPDATE");
        assertThat(saved.getOperationName()).isEqualTo("修改大屏");
        assertThat(saved.getOperationKind()).isEqualTo(AuditOperationKind.UPDATE.code());
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

    @Test
    void recordsClassificationMissForUnknownAnalyticsActionInsteadOfTrustingOverrides() {
        when(actionCatalogService.resolve("analytics", "ANALYTICS_NEW_WIDGET_EXPORT")).thenReturn(Optional.empty());

        AuditActionRequest request = AuditActionRequest
            .builder("xiezm", "ANALYTICS_NEW_WIDGET_EXPORT")
            .sourceSystem("analytics")
            .moduleOverride("analytics.widget", "组件")
            .operationOverride("ANALYTICS_WIDGET_EXPORT", "导出组件", AuditOperationKind.EXPORT)
            .summary("导出新增组件")
            .allowEmptyTargets()
            .build();

        service.record(request);

        verify(actionCatalogService).recordMiss(eq(request), eq("NO_CATALOG_MATCH"));
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(captor.capture());
        AuditEntry saved = captor.getValue();
        assertThat(saved.getSourceSystem()).isEqualTo("analytics");
        assertThat(saved.getModuleKey()).isEqualTo("analytics.unclassified");
        assertThat(saved.getModuleName()).isEqualTo("未分类分析操作");
        assertThat(saved.getOperationCode()).isEqualTo("ANALYTICS_NEW_WIDGET_EXPORT");
        assertThat(saved.getOperationName()).isEqualTo("导出新增组件");
        assertThat(saved.getMetadata()).containsEntry("classificationReason", "NO_CATALOG_MATCH");
    }
}
