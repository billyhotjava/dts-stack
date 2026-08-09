package com.yuzhi.dts.platform.web.rest.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainCommandService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = CatalogDomainResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = "ROLE_INST_DATA_OWNER")
@Import({ CatalogDomainCommandService.class, ArchitectureDictionaryWriteGuard.class })
class CatalogDomainResourceWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogDomainRepository domainRepository;

    @MockBean
    private CatalogDatasetRepository datasetRepository;

    @MockBean
    private AuditService auditService;

    @MockBean
    private CatalogDomainVisibilityService visibilityService;

    @MockBean
    private CatalogAssetPortalService assetPortalService;

    @MockBean
    private CatalogResourceHelper catalogResourceHelper;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @MockBean
    private DbtImplementationDraftRejectionAudit dbtImplementationDraftRejectionAudit;

    @BeforeEach
    void setUp() {
        when(domainRepository.save(any(CatalogDomain.class))).thenAnswer(invocation -> {
            CatalogDomain domain = invocation.getArgument(0);
            if (domain.getId() == null) {
                domain.setId(UUID.randomUUID());
            }
            return domain;
        });
    }

    @Test
    void createDefaultsCatalogFactsAndReturnsTheExistingJsonShape() throws Exception {
        mockMvc
            .perform(
                post("/api/catalog/domains")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"项目管理\",\"code\":\"project_management\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("项目管理"))
            .andExpect(jsonPath("$.data.code").value("project_management"))
            .andExpect(jsonPath("$.data.lifecycleStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.accessPolicy").value("PUBLIC"));
    }

    @Test
    void createRejectsUnknownOrNullCatalogFactEnumsWithStableBadRequest() throws Exception {
        for (String body : new String[] {
            "{\"name\":\"x\",\"lifecycleStatus\":\"DELETED\"}",
            "{\"name\":\"x\",\"accessPolicy\":\"PRIVATE\"}",
            "{\"name\":\"x\",\"lifecycleStatus\":null}",
            "{\"name\":\"x\",\"accessPolicy\":null}",
        }) {
            mockMvc
                .perform(post("/api/catalog/domains").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void createRejectsClientSuppliedIdBeforeSaving() throws Exception {
        UUID existingId = UUID.randomUUID();

        mockMvc
            .perform(
                post("/api/catalog/domains")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"id\":\"" + existingId + "\",\"name\":\"伪造分类\",\"code\":\"forged\"}"
                    )
            )
            .andExpect(status().isBadRequest());

        verify(domainRepository, never()).save(any(CatalogDomain.class));
    }

    @Test
    void updateAcceptsOnlyKnownCatalogFactEnums() throws Exception {
        UUID id = UUID.randomUUID();
        CatalogDomain existing = new CatalogDomain();
        existing.setId(id);
        existing.setName("项目管理");
        when(domainRepository.findById(id)).thenReturn(Optional.of(existing));
        when(visibilityService.canMaintain(any(CatalogDomain.class))).thenReturn(true);

        mockMvc
            .perform(
                put("/api/catalog/domains/{id}", id)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"项目管理\",\"lifecycleStatus\":\"ARCHIVED\",\"accessPolicy\":\"RESTRICTED\"}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lifecycleStatus").value("ARCHIVED"))
            .andExpect(jsonPath("$.data.accessPolicy").value("RESTRICTED"));
    }

    @Test
    void restrictedCreateWithoutAnExistingPermissionFactIsForbidden() throws Exception {
        when(visibilityService.canMaintain(any(CatalogDomain.class))).thenReturn(false);

        mockMvc
            .perform(
                post("/api/catalog/domains")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"受限分类\",\"accessPolicy\":\"RESTRICTED\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_DEPT_DATA_OWNER")
    void departmentDataOwnerCannotWritePlatformArchitectureDictionary() throws Exception {
        mockMvc
            .perform(
                post("/api/catalog/domains")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"部门自建分类\",\"code\":\"dept_owned\"}")
            )
            .andExpect(status().isForbidden());

        verify(domainRepository, never()).save(any(CatalogDomain.class));
    }
}
