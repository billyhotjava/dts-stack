package com.yuzhi.dts.platform.web.rest.catalog;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.repository.catalog.CatalogClassificationMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogDbtLineageService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import com.yuzhi.dts.platform.service.catalog.CatalogMetadataService;
import com.yuzhi.dts.platform.service.openmetadata.OpenMetadataService;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CatalogDatasetMetadataDepartmentScopeTest {

    private static final UUID TABLE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDomainVisibilityService domainVisibilityService;

    @Mock
    private CatalogMaskingRuleRepository maskingRepository;

    @Mock
    private CatalogClassificationMappingRepository mappingRepository;

    @Mock
    private CatalogTableSchemaRepository tableSchemaRepository;

    @Mock
    private CatalogColumnSchemaRepository columnSchemaRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private CatalogFeatureProperties catalogFeatures;

    @Mock
    private OrganizationVisibilityService organizationVisibilityService;

    @Mock
    private OpenMetadataService openMetadataService;

    @Mock
    private CatalogMetadataService catalogMetadataService;

    @Mock
    private CatalogResourceHelper helper;

    @Mock
    private GovIndicatorDefinitionRepository indicatorRepository;

    @Mock
    private CatalogDbtLineageService dbtLineageService;

    @InjectMocks
    private CatalogDatasetResource resource;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void departmentUserCannotOverrideAuthenticatedDepartmentForMetadataList() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "dept-a");
        when(catalogMetadataService.listLocalTables(null, 50, "dept-a", SOURCE_ID)).thenReturn(emptyPage());

        resource.listTechMetadataTables(null, 50, SOURCE_ID, "dept-ba");

        verify(catalogMetadataService).listLocalTables(null, 50, "dept-a", SOURCE_ID);
    }

    @Test
    void departmentUserCannotOverrideAuthenticatedDepartmentForMetadataDetail() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER, "10");
        String fqn = "catalog:" + TABLE_ID;
        when(catalogMetadataService.isLocalFqn(fqn)).thenReturn(true);

        resource.getTechMetadataTableDetail(fqn, "10010");

        verify(catalogMetadataService).fetchLocalTableDetail(fqn, "10");
    }

    @Test
    void instituteUserCanExplicitlySwitchMetadataDepartmentScope() {
        authenticate(AuthoritiesConstants.INST_DATA_OWNER, "institute-root");
        when(catalogMetadataService.listLocalTables(null, 50, "dept-b", SOURCE_ID)).thenReturn(emptyPage());

        resource.listTechMetadataTables(null, 50, SOURCE_ID, " dept-b ");

        verify(catalogMetadataService).listLocalTables(null, 50, "dept-b", SOURCE_ID);
    }

    private static OpenMetadataService.OpenMetadataTablePage emptyPage() {
        return OpenMetadataService.OpenMetadataTablePage.empty(null, false, OpenMetadataService.SOURCE_CATALOG, "empty");
    }

    private static void authenticate(String role, String department) {
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject("actor")
            .claim("roles", List.of(role))
            .claim("dept_code", department)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
