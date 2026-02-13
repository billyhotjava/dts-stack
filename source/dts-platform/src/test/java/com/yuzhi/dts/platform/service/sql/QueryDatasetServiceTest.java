package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.sql.dto.QueryDatasetResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class QueryDatasetServiceTest {

    @Mock
    private QueryDatasetAssetRepository assetRepository;

    @Mock
    private QueryDatasetVersionRepository versionRepository;

    @Mock
    private QueryExecutionRepository executionRepository;

    @Mock
    private ResultSetRepository resultSetRepository;

    @InjectMocks
    private QueryDatasetService service;

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldUseNormalizedDeptMatching_forNonAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice", "n/a", org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE"))
        );

        QueryDatasetAsset deptVisible = asset("dept-visible", "DEPT_D1", "bob", true);
        QueryDatasetAsset deptInvisible = asset("dept-invisible", "D2", "carol", true);
        QueryDatasetAsset ownerNullOther = asset("owner-null-other", null, "dave", true);
        QueryDatasetAsset ownerNullOwn = asset("owner-null-own", null, "alice", true);

        when(assetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()).thenReturn(List.of(deptVisible, deptInvisible, ownerNullOther));
        when(assetRepository.findByCreatedByOrderByLastModifiedDateDesc("alice")).thenReturn(List.of(ownerNullOwn));

        List<QueryDatasetResponse> result = service.list("D1");

        assertThat(result).extracting(QueryDatasetResponse::name).containsExactly("dept-visible", "owner-null-own");
    }

    @Test
    void list_shouldReturnAllEnabled_forCatalogMaintainer() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "maintainer",
                "n/a",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_DEPT_DATA_OWNER")
            )
        );

        QueryDatasetAsset a1 = asset("a1", "D1", "u1", true);
        QueryDatasetAsset a2 = asset("a2", null, "u2", true);
        when(assetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()).thenReturn(List.of(a1, a2));

        List<QueryDatasetResponse> result = service.list("D1");

        assertThat(result).extracting(QueryDatasetResponse::name).containsExactly("a1", "a2");
        verify(assetRepository, never()).findByCreatedByOrderByLastModifiedDateDesc(anyString());
    }

    private QueryDatasetAsset asset(String name, String ownerDept, String createdBy, boolean enabled) {
        QueryDatasetAsset asset = new QueryDatasetAsset();
        asset.setId(UUID.randomUUID());
        asset.setName(name);
        asset.setOwnerDept(ownerDept);
        asset.setCreatedBy(createdBy);
        asset.setEnabled(enabled);
        asset.setStatus("DRAFT");
        asset.setRefreshStrategy("MANUAL");
        asset.setSqlText("select 1");
        return asset;
    }
}
