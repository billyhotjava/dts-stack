package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogTagAuditServiceTest {

    private static final UUID TAG_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TAG_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AuditService auditService;

    @Mock
    private CatalogTagRepository tagRepository;

    @InjectMocks
    private CatalogTagAuditService service;

    @Test
    void assetSuccessIncludesStableTagCodesAndMutationCounts() {
        when(tagRepository.findAllById(List.of(TAG_B, TAG_A))).thenReturn(List.of(tag(TAG_A, "PII"), tag(TAG_B, "CORE")));

        service.assetSuccess(
            CatalogTagAuditService.ASSET_TAG_CREATE,
            "SCREEN:screen:114",
            List.of(TAG_B, TAG_A, TAG_B),
            Map.of("created", 2, "skipped", 0)
        );

        verify(auditService)
            .auditAction(
                eq("CATALOG_ASSET_TAG_CREATE"),
                eq(AuditStage.SUCCESS),
                eq("SCREEN:screen:114"),
                argThat(payload -> {
                    assertThat(payload)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("tagCount", 2)
                        .containsEntry("created", 2)
                        .containsEntry("skipped", 0)
                        .containsEntry("tagCodes", List.of("CORE", "PII"))
                        .containsEntry("tagIds", List.of(TAG_B.toString(), TAG_A.toString()));
                    return true;
                })
            );
    }

    @Test
    void permissionFailureUsesStableReasonCodeAndStillIncludesTagCodes() {
        when(tagRepository.findAllById(List.of(TAG_A))).thenReturn(List.of(tag(TAG_A, "PII")));
        CatalogAssetTagPermissionException failure = new CatalogAssetTagPermissionException(
            HttpStatus.FORBIDDEN,
            "INSUFFICIENT_PERMISSION",
            CatalogAssetType.SCREEN,
            "screen:114",
            "资产需要 EDIT 或 MANAGE 权限"
        );

        service.assetFailure(
            CatalogTagAuditService.ASSET_TAG_CREATE,
            "SCREEN:screen:114",
            List.of(TAG_A),
            failure,
            Map.of("assetCount", 1, "deniedCount", 1)
        );

        verify(auditService)
            .auditAction(
                eq("CATALOG_ASSET_TAG_CREATE"),
                eq(AuditStage.FAIL),
                eq("SCREEN:screen:114"),
                argThat(payload -> {
                    assertThat(payload)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("reasonCode", "INSUFFICIENT_PERMISSION")
                        .containsEntry("deniedCount", 1)
                        .containsEntry("tagCodes", List.of("PII"));
                    return true;
                })
            );
    }

    @Test
    void governanceFailureMapsResponseStatusToReadableCode() {
        service.failure(
            CatalogTagAuditService.TAG_CATEGORY_CREATE,
            "CUSTOM",
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类编码已存在"),
            Map.of("code", "CUSTOM")
        );

        verify(auditService)
            .auditAction(
                eq("CATALOG_TAG_CATEGORY_CREATE"),
                eq(AuditStage.FAIL),
                eq("CUSTOM"),
                argThat(payload ->
                    payload instanceof Map<?, ?> map &&
                    "BAD_REQUEST".equals(map.get("reasonCode")) &&
                    "CUSTOM".equals(map.get("code"))
                )
            );
    }

    @Test
    void tagCodeLookupFailureDoesNotSuppressTheBusinessAudit() {
        when(tagRepository.findAllById(List.of(TAG_A))).thenThrow(new IllegalStateException("read failed"));

        service.assetSuccess(
            CatalogTagAuditService.ASSET_TAG_DELETE,
            "SCREEN:screen:114",
            List.of(TAG_A),
            Map.of("removed", 1)
        );

        verify(auditService)
            .auditAction(
                eq("CATALOG_ASSET_TAG_DELETE"),
                eq(AuditStage.SUCCESS),
                eq("SCREEN:screen:114"),
                argThat(payload ->
                    payload instanceof Map<?, ?> map &&
                    List.of(TAG_A.toString()).equals(map.get("tagIds")) &&
                    List.of().equals(map.get("tagCodes")) &&
                    "FAILED".equals(map.get("tagCodeResolution"))
                )
            );
    }

    @Test
    void auditStorageFailureNeverEscapesIntoTheBusinessOperation() {
        doThrow(new IllegalStateException("audit storage unavailable"))
            .when(auditService)
            .auditAction(eq(CatalogTagAuditService.TAG_DELETE), eq(AuditStage.SUCCESS), eq("CUSTOM-TAG"), argThat(payload -> true));
        doThrow(new IllegalStateException("audit storage unavailable"))
            .when(auditService)
            .auditAction(eq(CatalogTagAuditService.TAG_DELETE), eq(AuditStage.FAIL), eq("CUSTOM-TAG"), argThat(payload -> true));

        assertThatCode(() ->
                service.success(
                    CatalogTagAuditService.TAG_DELETE,
                    "CUSTOM-TAG",
                    Map.of("id", TAG_A.toString(), "code", "CUSTOM-TAG")
                )
            )
            .doesNotThrowAnyException();
        assertThatCode(() ->
                service.failure(
                    CatalogTagAuditService.TAG_DELETE,
                    "CUSTOM-TAG",
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "删除失败"),
                    Map.of("id", TAG_A.toString(), "code", "CUSTOM-TAG")
                )
            )
            .doesNotThrowAnyException();
    }

    private CatalogTag tag(UUID id, String code) {
        CatalogTag tag = new CatalogTag();
        tag.setId(id);
        tag.setCode(code);
        return tag;
    }
}
