package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionActivityService;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.service.audit.AuditFlowManager;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionException;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagReadVisibilityService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard.AuthorizedAssets;
import com.yuzhi.dts.platform.service.catalog.CatalogTagAuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogTagGovernanceGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogTagSeedService;
import com.yuzhi.dts.platform.service.catalog.CatalogTagService;
import com.yuzhi.dts.platform.service.catalog.CatalogTagService.DeleteSnapshot;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.AssetTagMutationResult;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagResult;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagCategoryDto;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagCategoryRequest;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagRequest;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagSeedReport;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(
    value = CatalogTagResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
    }
)
@AutoConfigureMockMvc
@Import({ CatalogTagResourceTest.MethodSecurityConfig.class, CatalogTagGovernanceGuard.class })
class CatalogTagResourceTest {

    private static final UUID CATEGORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TAG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogTagService tagService;

    @MockBean
    private CatalogAssetTagService assetTagService;

    @MockBean
    private CatalogAssetTagReadVisibilityService assetTagReadVisibility;

    @MockBean
    private CatalogTagSeedService seedService;

    @MockBean
    private CatalogAssetTagWriteGuard assetTagWriteGuard;

    @MockBean
    private CatalogTagAuditService tagAuditService;

    @MockBean
    private AuditFlowManager auditFlowManager;

    @MockBean
    private PortalSessionActivityService portalSessionActivityService;

    @MockBean
    private PortalSessionCookieService portalSessionCookieService;

    @Test
    @WithMockUser(authorities = "ROLE_EMPLOYEE")
    void ordinaryUserCanReadAndTagListDefaultsToTenItemsPerPage() throws Exception {
        when(tagService.listCategoryTree()).thenReturn(List.of());
        when(tagService.listTags(isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenAnswer(invocation -> {
                Pageable pageable = invocation.getArgument(3);
                return new PageImpl<>(List.of(), pageable, 0);
            });

        mockMvc.perform(get("/api/catalog/tag-categories")).andExpect(status().isOk());
        mockMvc
            .perform(get("/api/catalog/tags"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void categoryAndTagRequestConstraintsMirrorDatabaseLengths() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var categoryViolations = validator.validate(
                new CatalogTagCategoryRequest(
                    "CATEGORY",
                    "名".repeat(129),
                    null,
                    0,
                    false,
                    true,
                    "说".repeat(513)
                )
            );
            var tagViolations = validator.validate(
                new CatalogTagRequest(
                    UUID.randomUUID(),
                    "TAG",
                    "名".repeat(129),
                    "#1234567890123456",
                    false,
                    true,
                    "说".repeat(513)
                )
            );

            assertThat(categoryViolations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "description");
            assertThat(tagViolations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "color", "description");
        }
    }

    @Test
    @WithMockUser(authorities = "ROLE_EMPLOYEE")
    void invalidPaginationReturnsReadableBadRequest() throws Exception {
        mockMvc
            .perform(get("/api/catalog/tags").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("page")));
        mockMvc
            .perform(get("/api/catalog/tags").param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("size")));
        mockMvc
            .perform(get("/api/catalog/tags").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("100")));
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void unreadableAssetTagListIsHiddenAndNeverQueried() throws Exception {
        AssetRef asset = new AssetRef("SCREEN", "screen:114");
        when(assetTagReadVisibility.filterReadable(List.of(asset), null)).thenReturn(List.of());

        mockMvc
            .perform(
                get("/api/catalog/asset-tags")
                    .param("assetType", asset.assetType())
                    .param("assetKey", asset.assetKey())
            )
            .andExpect(status().isNotFound());

        verifyNoInteractions(assetTagService);
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void readableAssetTagListReturnsTags() throws Exception {
        AssetRef asset = new AssetRef("SCREEN", "screen:114");
        CatalogTagDto tag = new CatalogTagDto(
            TAG_ID,
            CATEGORY_ID,
            "CUSTOM-TAG",
            "自定义标签",
            null,
            false,
            true,
            null
        );
        when(assetTagReadVisibility.filterReadable(List.of(asset), "D01")).thenReturn(List.of(asset));
        when(assetTagService.listAssetTags(asset.assetType(), asset.assetKey())).thenReturn(List.of(tag));

        mockMvc
            .perform(
                get("/api/catalog/asset-tags")
                    .header("X-Active-Dept", "D01")
                    .param("assetType", asset.assetType())
                    .param("assetKey", asset.assetKey())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("CUSTOM-TAG"));
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void assetTagCapabilityReturnsTrueWithoutMutation() throws Exception {
        AssetRef asset = new AssetRef("CATALOG_DOMAIN", "tenant:default/env:prod/dialect:generic/catalog_domain:pjm-qa");
        when(assetTagWriteGuard.canTag(asset)).thenReturn(true);

        mockMvc
            .perform(
                get("/api/catalog/asset-tags/capability")
                    .param("assetType", asset.assetType())
                    .param("assetKey", asset.assetKey())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.canTag").value(true));

        verifyNoInteractions(assetTagService, tagAuditService);
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void unresolvableAssetTagCapabilityFailsClosedWithoutMutation() throws Exception {
        AssetRef asset = new AssetRef("CATALOG_DOMAIN", "unresolvable");
        when(assetTagWriteGuard.canTag(asset)).thenReturn(false);

        mockMvc
            .perform(
                get("/api/catalog/asset-tags/capability")
                    .param("assetType", asset.assetType())
                    .param("assetKey", asset.assetKey())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.canTag").value(false));

        verifyNoInteractions(assetTagService, tagAuditService);
    }

    @Test
    void anonymousWriteIsUnauthorizedWithSecurityFiltersEnabled() throws Exception {
        mockMvc
            .perform(
                post("/api/catalog/tag-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(categoryBody())
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(tagService);
    }

    @Test
    @WithMockUser(authorities = "ROLE_EMPLOYEE")
    void ordinaryUserIsForbiddenFromGovernanceWriteEndpoints() throws Exception {
        for (MockHttpServletRequestBuilder request : ordinaryUserGovernanceWriteRequests()) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }

        verifyNoInteractions(tagService, assetTagService, seedService);
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void ordinaryGrantedUserCanTagAssetAndWritesCanonicalSuccessAudit() throws Exception {
        AssetRef asset = new AssetRef("SCREEN", "screen:114");
        when(assetTagWriteGuard.authorizeAll(List.of(asset)))
            .thenReturn(new AuthorizedAssets(List.of(asset), "analyst"));
        when(assetTagService.tagAsset("SCREEN", "screen:114", List.of(TAG_ID), "analyst"))
            .thenReturn(new AssetTagMutationResult(1, 0, 0));

        mockMvc
            .perform(
                post("/api/catalog/asset-tags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(assetBody("SCREEN", "screen:114"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.created").value(1));

        verify(assetTagWriteGuard).authorizeAll(List.of(asset));
        verify(assetTagService).tagAsset("SCREEN", "screen:114", List.of(TAG_ID), "analyst");
        verify(tagAuditService)
            .assetSuccess(
                eq(CatalogTagAuditService.ASSET_TAG_CREATE),
                eq("SCREEN:screen:114"),
                eq(List.of(TAG_ID)),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    Integer.valueOf(1).equals(map.get("created"))
                )
            );
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void ordinaryGrantedUserCanUntagAssetAndWritesCanonicalSuccessAudit() throws Exception {
        AssetRef asset = new AssetRef("SCREEN", "screen:114");
        when(assetTagWriteGuard.authorizeAll(List.of(asset)))
            .thenReturn(new AuthorizedAssets(List.of(asset), "analyst"));
        when(assetTagService.untagAsset("SCREEN", "screen:114", List.of(TAG_ID)))
            .thenReturn(new AssetTagMutationResult(0, 0, 1));

        mockMvc
            .perform(
                delete("/api/catalog/asset-tags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(assetBody("SCREEN", "screen:114"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.removed").value(1));

        verify(assetTagWriteGuard).authorizeAll(List.of(asset));
        verify(assetTagService).untagAsset("SCREEN", "screen:114", List.of(TAG_ID));
        verify(tagAuditService)
            .assetSuccess(
                eq(CatalogTagAuditService.ASSET_TAG_DELETE),
                eq("SCREEN:screen:114"),
                eq(List.of(TAG_ID)),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    Integer.valueOf(1).equals(map.get("removed"))
                )
            );
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void mixedBatchPermissionDenialPerformsNoMutationAndWritesOneFailAudit() throws Exception {
        List<AssetRef> assets = List.of(
            new AssetRef("SCREEN", "screen:114"),
            new AssetRef("MODELING_SQL_MODEL", "tenant:default/env:prod/dialect:generic/modeling_sql_model:sales")
        );
        CatalogAssetTagPermissionException failure = new CatalogAssetTagPermissionException(
            HttpStatus.FORBIDDEN,
            "INSUFFICIENT_PERMISSION",
            null,
            assets.get(1).assetKey(),
            "至少一个资产未通过 WRITE 权限预检"
        );
        when(assetTagWriteGuard.authorizeAll(assets)).thenThrow(failure);

        mockMvc
            .perform(
                post("/api/catalog/asset-tags/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchAssetBody(assets))
            )
            .andExpect(status().isForbidden());

        verify(assetTagService, never()).batchTag(any(), any());
        verify(tagAuditService)
            .assetFailure(
                eq(CatalogTagAuditService.ASSET_TAG_BATCH_CREATE),
                eq("BATCH"),
                eq(List.of(TAG_ID)),
                eq(failure),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    Integer.valueOf(2).equals(map.get("assetCount")) &&
                    Integer.valueOf(1).equals(map.get("deniedCount"))
                )
            );
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void authorizedBatchWritesOneBusinessAuditInsteadOfPerAssetAudits() throws Exception {
        List<AssetRef> assets = List.of(
            new AssetRef("SCREEN", "screen:114"),
            new AssetRef("SCREEN", "screen:115")
        );
        when(assetTagWriteGuard.authorizeAll(assets))
            .thenReturn(new AuthorizedAssets(assets, "analyst"));
        when(assetTagService.batchTag(any(), eq("analyst")))
            .thenReturn(new BatchAssetTagResult(2, 2, 0, List.of()));

        mockMvc
            .perform(
                post("/api/catalog/asset-tags/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchAssetBody(assets))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assetCount").value(2))
            .andExpect(jsonPath("$.data.created").value(2));

        verify(assetTagWriteGuard).authorizeAll(assets);
        verify(assetTagService).batchTag(argThat(request -> request.assets().equals(assets)), eq("analyst"));
        verify(tagAuditService)
            .assetSuccess(
                eq(CatalogTagAuditService.ASSET_TAG_BATCH_CREATE),
                eq("BATCH"),
                eq(List.of(TAG_ID)),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    Integer.valueOf(2).equals(map.get("assetCount")) &&
                    Integer.valueOf(2).equals(map.get("created"))
                )
            );
        verify(tagAuditService, never())
            .assetSuccess(eq(CatalogTagAuditService.ASSET_TAG_CREATE), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "employee", authorities = "ROLE_EMPLOYEE")
    void governanceWriteDenialProducesCanonicalFailAudit() throws Exception {
        mockMvc
            .perform(
                post("/api/catalog/tag-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(categoryBody())
            )
            .andExpect(status().isForbidden());

        verify(tagService, never()).createCategory(any(CatalogTagCategoryRequest.class));
        verify(tagAuditService)
            .failure(
                eq(CatalogTagAuditService.TAG_CATEGORY_CREATE),
                eq("CUSTOM"),
                argThat(failure ->
                    failure instanceof CatalogAssetTagPermissionException exception &&
                    "FORBIDDEN".equals(exception.reasonCode())
                ),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    "CUSTOM".equals(map.get("code"))
                )
            );
    }

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void catalogMaintainerCanWriteThroughTheLocalGovernanceGuard() throws Exception {
        CatalogTagCategoryDto created = new CatalogTagCategoryDto(
            CATEGORY_ID,
            "CUSTOM",
            "自定义分类",
            null,
            10,
            false,
            true,
            null,
            0,
            List.of()
        );
        when(tagService.createCategory(any(CatalogTagCategoryRequest.class))).thenReturn(created);

        mockMvc
            .perform(
                post("/api/catalog/tag-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(categoryBody())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("CUSTOM"));

        verify(tagService).createCategory(any(CatalogTagCategoryRequest.class));
        verify(tagAuditService)
            .success(
                eq(CatalogTagAuditService.TAG_CATEGORY_CREATE),
                eq("CUSTOM"),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    CATEGORY_ID.toString().equals(map.get("id")) &&
                    "CUSTOM".equals(map.get("code"))
                )
            );
    }

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void remainingGovernanceWritesUseTheirExactCanonicalSuccessActions() throws Exception {
        CatalogTagCategoryDto category = new CatalogTagCategoryDto(
            CATEGORY_ID,
            "CUSTOM",
            "自定义分类",
            null,
            10,
            false,
            true,
            null,
            0,
            List.of()
        );
        CatalogTagDto tag = new CatalogTagDto(
            TAG_ID,
            CATEGORY_ID,
            "CUSTOM-TAG",
            "自定义标签",
            null,
            false,
            true,
            null
        );
        CatalogTagSeedReport report = new CatalogTagSeedReport(
            "dts-common-catalog-tags",
            "1.0.0",
            true,
            1,
            2,
            0,
            0,
            List.of("CORE"),
            List.of()
        );
        when(tagService.updateCategory(eq(CATEGORY_ID), any(CatalogTagCategoryRequest.class))).thenReturn(category);
        when(tagService.createTag(any(CatalogTagRequest.class))).thenReturn(tag);
        when(tagService.updateTag(eq(TAG_ID), any(CatalogTagRequest.class))).thenReturn(tag);
        when(tagService.getCategoryDeleteSnapshot(CATEGORY_ID))
            .thenReturn(new DeleteSnapshot(CATEGORY_ID, "CUSTOM"));
        when(tagService.getTagDeleteSnapshot(TAG_ID))
            .thenReturn(new DeleteSnapshot(TAG_ID, "CUSTOM-TAG"));
        when(seedService.install()).thenReturn(report);

        mockMvc
            .perform(
                put("/api/catalog/tag-categories/{id}", CATEGORY_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(categoryBody())
            )
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/catalog/tag-categories/{id}", CATEGORY_ID)).andExpect(status().isOk());
        mockMvc
            .perform(post("/api/catalog/tags").contentType(MediaType.APPLICATION_JSON).content(tagBody()))
            .andExpect(status().isOk());
        mockMvc
            .perform(
                put("/api/catalog/tags/{id}", TAG_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(tagBody())
            )
            .andExpect(status().isOk());
        mockMvc
            .perform(delete("/api/catalog/tags/{id}", TAG_ID).param("force", "true"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/catalog/tags/builtin/install")).andExpect(status().isOk());

        verify(tagAuditService)
            .success(eq(CatalogTagAuditService.TAG_CATEGORY_UPDATE), eq("CUSTOM"), any());
        verify(tagAuditService)
            .success(
                eq(CatalogTagAuditService.TAG_CATEGORY_DELETE),
                eq("CUSTOM"),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    CATEGORY_ID.toString().equals(map.get("id")) &&
                    "CUSTOM".equals(map.get("code"))
                )
            );
        verify(tagAuditService)
            .success(eq(CatalogTagAuditService.TAG_CREATE), eq("CUSTOM-TAG"), any());
        verify(tagAuditService)
            .success(eq(CatalogTagAuditService.TAG_UPDATE), eq("CUSTOM-TAG"), any());
        verify(tagAuditService)
            .success(
                eq(CatalogTagAuditService.TAG_DELETE),
                eq("CUSTOM-TAG"),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    TAG_ID.toString().equals(map.get("id")) &&
                    "CUSTOM-TAG".equals(map.get("code")) &&
                    Boolean.TRUE.equals(map.get("force"))
                )
            );
        verify(tagAuditService)
            .success(
                eq(CatalogTagAuditService.BUILTIN_INSTALL),
                eq("dts-common-catalog-tags"),
                any()
            );
    }

    @Test
    @WithMockUser(username = "owner", authorities = "ROLE_INST_DATA_OWNER")
    void deleteTagFailureUsesThePreDeleteBusinessCodeAndPreservesTheOriginalFailure() throws Exception {
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签已用于资产");
        when(tagService.getTagDeleteSnapshot(TAG_ID))
            .thenReturn(new DeleteSnapshot(TAG_ID, "CUSTOM-TAG"));
        doThrow(failure).when(tagService).deleteTag(TAG_ID, false);

        mockMvc
            .perform(delete("/api/catalog/tags/{id}", TAG_ID))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("标签已用于资产")));

        verify(tagAuditService)
            .failure(
                eq(CatalogTagAuditService.TAG_DELETE),
                eq("CUSTOM-TAG"),
                eq(failure),
                argThat(payload ->
                    payload instanceof java.util.Map<?, ?> map &&
                    TAG_ID.toString().equals(map.get("id")) &&
                    "CUSTOM-TAG".equals(map.get("code")) &&
                    Boolean.FALSE.equals(map.get("force"))
                )
            );
    }

    @Test
    @WithMockUser(authorities = "ROLE_INST_DATA_OWNER")
    void invalidRequestBodyIsRejectedBeforeTheMaintainerServiceCall() throws Exception {
        mockMvc
            .perform(
                post("/api/catalog/tag-categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"CUSTOM","name":"","sortOrder":10,"enabled":true}
                        """
                    )
            )
            .andExpect(status().isBadRequest());

        verify(tagService, never()).createCategory(any(CatalogTagCategoryRequest.class));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INST_DATA_OWNER")
    void batchOverFiveHundredReturnsReadableBadRequest() throws Exception {
        StringBuilder assets = new StringBuilder("[");
        for (int i = 0; i < 501; i++) {
            if (i > 0) {
                assets.append(',');
            }
            assets.append("{\"assetType\":\"METRIC\",\"assetKey\":\"metric:core/m").append(i).append("\"}");
        }
        assets.append(']');
        String body = "{\"assets\":" + assets + ",\"tagIds\":[\"" + TAG_ID + "\"]}";

        mockMvc
            .perform(
                post("/api/catalog/asset-tags/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest());

        verify(assetTagService, never()).batchTag(any(), any());
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void singleAssetOverOneHundredTagsIsRejectedBeforePermissionAndMutation() throws Exception {
        String body =
            """
            {
              "assetType": "METRIC",
              "assetKey": "metric:core/revenue",
              "tagIds": [%s]
            }
            """.formatted(tagIdsJson(101));

        mockMvc
            .perform(
                post("/api/catalog/asset-tags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(assetTagWriteGuard, assetTagService);
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void singleAssetUntagOverOneHundredTagsIsRejectedBeforePermissionAndMutation() throws Exception {
        String body =
            """
            {
              "assetType": "METRIC",
              "assetKey": "metric:core/revenue",
              "tagIds": [%s]
            }
            """.formatted(tagIdsJson(101));

        mockMvc
            .perform(
                delete("/api/catalog/asset-tags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(assetTagWriteGuard, assetTagService);
    }

    @Test
    @WithMockUser(username = "analyst", authorities = "ROLE_EMPLOYEE")
    void batchOverOneHundredTagsIsRejectedBeforePermissionAndMutation() throws Exception {
        String body =
            """
            {
              "assets": [{"assetType": "METRIC", "assetKey": "metric:core/revenue"}],
              "tagIds": [%s]
            }
            """.formatted(tagIdsJson(101));

        mockMvc
            .perform(
                post("/api/catalog/asset-tags/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(assetTagWriteGuard, assetTagService);
    }

    private String tagIdsJson(int count) {
        return java.util.stream.IntStream
            .range(0, count)
            .mapToObj(index -> "\"" + new UUID(0, (index % 100) + 1L) + "\"")
            .collect(java.util.stream.Collectors.joining(","));
    }

    private List<MockHttpServletRequestBuilder> ordinaryUserGovernanceWriteRequests() {
        String tagBody =
            """
            {
              "categoryId": "%s",
              "code": "CUSTOM-TAG",
              "name": "自定义标签",
              "enabled": true
            }
            """.formatted(CATEGORY_ID);
        return List.of(
            post("/api/catalog/tag-categories").contentType(MediaType.APPLICATION_JSON).content(categoryBody()),
            put("/api/catalog/tag-categories/{id}", CATEGORY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryBody()),
            delete("/api/catalog/tag-categories/{id}", CATEGORY_ID),
            post("/api/catalog/tags").contentType(MediaType.APPLICATION_JSON).content(tagBody),
            put("/api/catalog/tags/{id}", TAG_ID).contentType(MediaType.APPLICATION_JSON).content(tagBody),
            delete("/api/catalog/tags/{id}", TAG_ID),
            post("/api/catalog/tags/builtin/install")
        );
    }

    private String assetBody(String assetType, String assetKey) {
        return """
        {
          "assetType": "%s",
          "assetKey": "%s",
          "tagIds": ["%s"]
        }
        """.formatted(assetType, assetKey, TAG_ID);
    }

    private String batchAssetBody(List<AssetRef> assets) {
        String assetJson = assets
            .stream()
            .map(asset -> "{\"assetType\":\"" + asset.assetType() + "\",\"assetKey\":\"" + asset.assetKey() + "\"}")
            .collect(java.util.stream.Collectors.joining(","));
        return "{\"assets\":[" + assetJson + "],\"tagIds\":[\"" + TAG_ID + "\"]}";
    }

    private String categoryBody() {
        return """
        {"code":"CUSTOM","name":"自定义分类","sortOrder":10,"enabled":true}
        """;
    }

    private String tagBody() {
        return """
        {
          "categoryId": "%s",
          "code": "CUSTOM-TAG",
          "name": "自定义标签",
          "enabled": true
        }
        """.formatted(CATEGORY_ID);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
            return http.build();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                User.withUsername("user").password("{noop}password").authorities("ROLE_USER").build()
            );
        }
    }
}
