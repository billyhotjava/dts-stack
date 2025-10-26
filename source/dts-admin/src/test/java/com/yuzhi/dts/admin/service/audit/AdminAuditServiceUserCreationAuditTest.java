package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.AuditProperties;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.AuditEventRepository;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceUserCreationAuditTest {

    @Mock
    private AuditEventRepository repository;

    @Mock
    private DtsCommonAuditClient auditClient;

    @Mock
    private AdminKeycloakUserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AuditResourceDictionaryService resourceDictionary;

    @Mock
    private OperationMappingEngine operationMappingEngine;

    private AuditProperties properties;
    private ObjectMapper objectMapper;
    private AdminAuditService service;

    @BeforeEach
    void setUp() {
        properties = new AuditProperties();
        objectMapper = new ObjectMapper();
        when(operationMappingEngine.resolveWithFallback(any())).thenReturn(Optional.empty());
        service = new AdminAuditService(
            repository,
            properties,
            auditClient,
            objectMapper,
            userRepository,
            organizationRepository,
            resourceDictionary,
            operationMappingEngine
        );
    }

    @Test
    void buildEntityShouldKeepRequestedUsernameWhenUserSnapshotMissing() throws Exception {
        AdminAuditService.PendingAuditEvent pending = new AdminAuditService.PendingAuditEvent();
        pending.occurredAt = Instant.parse("2025-10-23T01:57:20Z");
        pending.actor = "sysadmin";
        pending.module = "admin";
        pending.action = "ADMIN_USER_CREATE";
        pending.resourceType = "admin.user";
        pending.resourceId = "optest56";
        pending.requestUri = "/api/keycloak/users";
        pending.httpMethod = "POST";
        pending.result = "PENDING";
        pending.payload = Map.of("username", "optest56");

        AdminKeycloakUser admin = new AdminKeycloakUser();
        admin.setId(1L);
        admin.setUsername("sysadmin");
        admin.setFullName("系统管理员");
        when(userRepository.findByUsernameIgnoreCase("sysadmin")).thenReturn(Optional.of(admin));
        when(resourceDictionary.resolveLabel("admin_keycloak_user")).thenReturn(Optional.of("用户"));
        when(resourceDictionary.resolveCategory("admin.user")).thenReturn(Optional.of("用户管理"));
        when(resourceDictionary.resolveCategory("admin")).thenReturn(Optional.of("用户管理"));

        Method buildEntity = AdminAuditService.class.getDeclaredMethod(
            "buildEntity",
            AdminAuditService.PendingAuditEvent.class,
            String.class
        );
        buildEntity.setAccessible(true);
        AuditEvent entity = (AuditEvent) buildEntity.invoke(service, pending, "");

        assertThat(entity).isNotNull();
        assertThat(entity.getRequestUri()).isEqualTo("/api/keycloak/users");

        Map<String, Object> details = objectMapper.readValue(
            entity.getDetails(),
            new TypeReference<Map<String, Object>>() {}
        );

        assertThat(details.get("目标引用")).isEqualTo("optest56");
        assertThat(details).doesNotContainKeys("target_ref");
        assertThat(String.valueOf(details.get("目标ID"))).isNotBlank();
        assertThat(String.valueOf(details.get("目标ID"))).isNotEqualToIgnoringCase("sysadmin");

        verify(userRepository, never()).findById(anyLong());
    }
}
