package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.domain.audit.AuditActionCatalogEntry;
import com.yuzhi.dts.admin.domain.audit.AuditModuleCatalog;
import com.yuzhi.dts.admin.repository.audit.AuditActionCatalogRepository;
import com.yuzhi.dts.admin.repository.audit.AuditModuleCatalogRepository;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import com.yuzhi.dts.common.audit.AuditStage;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditActionCatalogBootstrapServiceTest {

    private AuditActionCatalog commonCatalog;
    private AuditModuleCatalogRepository moduleRepository;
    private AuditActionCatalogRepository actionRepository;
    private AuditActionCatalogBootstrapService service;

    @BeforeEach
    void setUp() {
        commonCatalog = mock(AuditActionCatalog.class);
        moduleRepository = mock(AuditModuleCatalogRepository.class);
        actionRepository = mock(AuditActionCatalogRepository.class);
        service = new AuditActionCatalogBootstrapService(commonCatalog, moduleRepository, actionRepository);
    }

    @Test
    void seedsMissingPlatformActionsFromCommonCatalogWithoutOverwritingDbGovernance() {
        AuditActionDefinition definition = new AuditActionDefinition(
            "GOV_ISSUE_CREATE",
            "创建治理问题",
            "governance",
            "治理中心",
            "governance.issue",
            "问题管理",
            false,
            Set.of(AuditStage.SUCCESS)
        );
        when(commonCatalog.listAll()).thenReturn(java.util.List.of(definition));
        when(moduleRepository.findFirstBySourceSystemIgnoreCaseAndModuleKeyIgnoreCase("platform", "governance.issue"))
            .thenReturn(Optional.empty());
        when(actionRepository.findFirstBySourceSystemIgnoreCaseAndActionCodeIgnoreCase("platform", "GOV_ISSUE_CREATE"))
            .thenReturn(Optional.empty());

        service.seedMissingCatalogEntries();

        ArgumentCaptor<AuditModuleCatalog> moduleCaptor = ArgumentCaptor.forClass(AuditModuleCatalog.class);
        verify(moduleRepository).save(moduleCaptor.capture());
        assertThat(moduleCaptor.getValue().getSourceSystem()).isEqualTo("platform");
        assertThat(moduleCaptor.getValue().getModuleKey()).isEqualTo("governance.issue");
        assertThat(moduleCaptor.getValue().getModuleName()).isEqualTo("问题管理");

        ArgumentCaptor<AuditActionCatalogEntry> actionCaptor = ArgumentCaptor.forClass(AuditActionCatalogEntry.class);
        verify(actionRepository).save(actionCaptor.capture());
        AuditActionCatalogEntry saved = actionCaptor.getValue();
        assertThat(saved.getSourceSystem()).isEqualTo("platform");
        assertThat(saved.getActionCode()).isEqualTo("GOV_ISSUE_CREATE");
        assertThat(saved.getModuleKey()).isEqualTo("governance.issue");
        assertThat(saved.getModuleName()).isEqualTo("问题管理");
        assertThat(saved.getOperationCode()).isEqualTo("GOV_ISSUE_CREATE");
        assertThat(saved.getOperationName()).isEqualTo("创建治理问题");
        assertThat(saved.getOperationKind()).isEqualTo("CREATE");
        assertThat(saved.getResourceType()).isEqualTo("governance.issue");
        assertThat(saved.getAllowEmptyTargets()).isTrue();
    }

    @Test
    void keepsExistingDbActionAsAuthoritative() {
        AuditActionDefinition definition = new AuditActionDefinition(
            "REPORT_VIEW",
            "查看报表",
            "visualization",
            "BI 分析",
            "visualization.report",
            "报表",
            false,
            Set.of(AuditStage.SUCCESS)
        );
        when(commonCatalog.listAll()).thenReturn(java.util.List.of(definition));
        when(actionRepository.findFirstBySourceSystemIgnoreCaseAndActionCodeIgnoreCase("platform", "REPORT_VIEW"))
            .thenReturn(Optional.of(new AuditActionCatalogEntry()));

        service.seedMissingCatalogEntries();

        verify(moduleRepository, never()).save(any());
        verify(actionRepository, never()).save(any());
    }
}
