package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetDirectoryReadAdapter.OwnerAsset;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CatalogAssetDirectoryVisibilityPolicyTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void instituteDataOwnerCanReadGlobalOwnerAssetWithoutInventedClassification() {
        authenticate(AuthoritiesConstants.INST_DATA_OWNER);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogAssetDirectoryVisibilityPolicy policy = new CatalogAssetDirectoryVisibilityPolicy(accessChecker);

        assertThat(policy.canRead(owner(null, null), null)).isTrue();

        verify(accessChecker, never()).canRead(any());
    }

    @Test
    void departmentScopeAcceptsOneExactOwnerFromARegisteredDepartmentList() {
        authenticate(AuthoritiesConstants.DEPT_DATA_OWNER);
        AccessChecker accessChecker = mock(AccessChecker.class);
        when(accessChecker.canRead(any())).thenReturn(true);
        when(
            accessChecker.departmentAllowedExact(
                argThat(dataset -> "D02".equals(dataset.getOwnerDept())),
                eq("D02")
            )
        ).thenReturn(true);
        CatalogAssetDirectoryVisibilityPolicy policy = new CatalogAssetDirectoryVisibilityPolicy(accessChecker);

        assertThat(policy.canRead(owner("INTERNAL", "[D01,D02]"), "D02")).isTrue();

        verify(accessChecker).canRead(any(CatalogDataset.class));
    }

    private OwnerAsset owner(String classification, String ownerDept) {
        UUID id = UUID.randomUUID();
        return new OwnerAsset(
            id,
            CatalogAssetType.SEMANTIC_MODEL,
            CatalogAssetKey.semanticModel(id.toString()),
            "测试模型",
            null,
            "FACT",
            "数据建模",
            null,
            classification,
            "DWD",
            "xiezm",
            ownerDept,
            "PUBLISHED",
            "GOVERNED",
            "UNKNOWN",
            Instant.now(),
            "/modeling/models/" + id
        );
    }

    private void authenticate(String authority) {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "xiezm",
                    "n/a",
                    List.of(new SimpleGrantedAuthority(authority))
                )
            );
    }
}
