package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentityType;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DeriveCommand;
import com.yuzhi.dts.platform.service.sql.dto.PublishQueryDatasetRequest;
import com.yuzhi.dts.platform.service.sql.dto.QueryDatasetResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private CanonicalModelIdentityReadPort canonicalModelIdentityReadPort;

    @Mock
    private CatalogConsumerClassificationService consumerClassificationService;

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

        when(canonicalModelIdentityReadPort.findDbtModelsByResourceNames(Set.of())).thenReturn(Map.of());
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
        when(canonicalModelIdentityReadPort.findDbtModelsByResourceNames(Set.of())).thenReturn(Map.of());
        when(assetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()).thenReturn(List.of(a1, a2));

        List<QueryDatasetResponse> result = service.list("D1");

        assertThat(result).extracting(QueryDatasetResponse::name).containsExactly("a1", "a2");
        verify(assetRepository, never()).findByCreatedByOrderByLastModifiedDateDesc(anyString());
    }

    @Test
    void publish_shouldDeriveClassificationFromCanonicalDbtModelIdentity() {
        authenticateMaintainer();
        UUID datasetId = UUID.randomUUID();
        QueryDatasetAsset dataset = asset("预算执行查询", "D1", "alice", true);
        dataset.setId(datasetId);
        QueryDatasetVersion version = version(dataset, "select * from {{ ref('fct_budget_execution') }}");
        UUID implementationId = UUID.randomUUID();
        UUID modelSpecId = UUID.randomUUID();
        ModelIdentity model = new ModelIdentity(
            ModelIdentityType.DBT_MODEL,
            implementationId,
            modelSpecId,
            implementationId,
            "预算执行事实",
            3,
            "model.finance.fct_budget_execution"
        );
        when(assetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(versionRepository.findByDataset_IdAndVersionNo(datasetId, 1)).thenReturn(Optional.of(version));
        when(canonicalModelIdentityReadPort.findDbtModelsByResourceNames(Set.of("fct_budget_execution")))
            .thenReturn(Map.of("fct_budget_execution", model));
        when(versionRepository.findByDataset_IdOrderByVersionNoDesc(datasetId)).thenReturn(List.of(version));
        when(versionRepository.save(any(QueryDatasetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assetRepository.save(any(QueryDatasetAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.publish(datasetId, new PublishQueryDatasetRequest(1, "发布"), "D1");

        ArgumentCaptor<DeriveCommand> command = ArgumentCaptor.forClass(DeriveCommand.class);
        verify(consumerClassificationService).derive(command.capture());
        assertThat(command.getValue().upstreams())
            .singleElement()
            .satisfies(upstream -> assertThat(upstream.subjectKey()).isEqualTo(CatalogAssetKey.dbtModel(model.dbtUniqueId(), model.modelName())));
    }

    @Test
    void publish_shouldFailClosedWhenCanonicalDbtReferenceIsUnresolved() {
        authenticateMaintainer();
        UUID datasetId = UUID.randomUUID();
        QueryDatasetAsset dataset = asset("预算执行查询", "D1", "alice", true);
        dataset.setId(datasetId);
        QueryDatasetVersion version = version(dataset, "select * from {{ ref('missing_model') }}");
        when(assetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(versionRepository.findByDataset_IdAndVersionNo(datasetId, 1)).thenReturn(Optional.of(version));
        when(canonicalModelIdentityReadPort.findDbtModelsByResourceNames(Set.of("missing_model"))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.publish(datasetId, new PublishQueryDatasetRequest(1, null), "D1"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("未解析的模型来源");

        verify(consumerClassificationService, never()).derive(any());
    }

    private void authenticateMaintainer() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "maintainer",
                "n/a",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_DEPT_DATA_OWNER")
            )
        );
    }

    private QueryDatasetVersion version(QueryDatasetAsset dataset, String sql) {
        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setId(UUID.randomUUID());
        version.setDataset(dataset);
        version.setVersionNo(1);
        version.setStatus("DRAFT");
        version.setSqlText(sql);
        return version;
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
