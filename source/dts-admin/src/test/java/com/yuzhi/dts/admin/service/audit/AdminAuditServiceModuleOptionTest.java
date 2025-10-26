package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.AuditProperties;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.AuditEventRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceModuleOptionTest {

    @Mock
    private AuditEventRepository repository;

    @Mock
    private DtsCommonAuditClient auditClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AdminKeycloakUserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AuditResourceDictionaryService resourceDictionary;

    @Mock
    private OperationMappingEngine operationMappingEngine;

    private AuditProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AuditProperties();
    }

    @Test
    void listModuleOptionsShouldMapCodesToLabels() {
        when(repository.findDistinctModules()).thenReturn(List.of("admin", "approval", "catalog", "admin", "  "));
        when(resourceDictionary.resolveCategory("admin")).thenReturn(Optional.of("用户管理"));
        when(resourceDictionary.resolveCategory("approval")).thenReturn(Optional.of("审批管理"));
        when(resourceDictionary.resolveCategory("catalog")).thenReturn(Optional.empty());
        when(resourceDictionary.resolveLabel("catalog")).thenReturn(Optional.of("数据资产"));

        AdminAuditService service = new AdminAuditService(
            repository,
            properties,
            auditClient,
            objectMapper,
            userRepository,
            organizationRepository,
            resourceDictionary,
            operationMappingEngine
        );

        List<AdminAuditService.ModuleOption> options = service.listModuleOptions();

        assertThat(options)
            .extracting(AdminAuditService.ModuleOption::code)
            .containsExactly("admin", "approval", "catalog");
        assertThat(options)
            .extracting(AdminAuditService.ModuleOption::label)
            .containsExactly("用户管理", "审批管理", "数据资产");
    }
}
