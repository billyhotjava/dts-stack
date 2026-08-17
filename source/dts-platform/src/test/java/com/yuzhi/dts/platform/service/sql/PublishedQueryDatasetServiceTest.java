package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.service.sql.PublishedQueryDatasetService.PublishedQueryDatasetQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PublishedQueryDatasetServiceTest {

    @Mock
    private QueryDatasetAssetRepository assetRepository;

    @Mock
    private QueryDatasetVersionRepository versionRepository;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldOnlyExposeReadyPublishedProjectionWithoutBaseSql() {
        authenticateMaintainer();
        QueryDatasetAsset asset = asset();
        QueryDatasetVersion version = publishedVersion(asset);
        when(assetRepository.findByEnabledTrueOrderByLastModifiedDateDesc()).thenReturn(List.of(asset));
        when(versionRepository.findByDataset_IdAndVersionNo(asset.getId(), 2)).thenReturn(Optional.of(version));

        PublishedQueryDatasetService service = new PublishedQueryDatasetService(
            assetRepository,
            versionRepository,
            new ObjectMapper()
        );
        var page = service.list(
            new PublishedQueryDatasetQuery(0, 10, "项目", null, null, "ADS", "DATA_INTERNAL"),
            "D1"
        );

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items())
            .singleElement()
            .satisfies(item -> {
                assertThat(item.datasetId()).isEqualTo(asset.getId());
                assertThat(item.version()).isEqualTo(2);
                assertThat(item.semanticContractVersion()).isEqualTo("r7");
                assertThat(item.contractChecksum()).isEqualTo(version.getSemanticContractChecksum());
                assertThat(item.semanticModelNames()).containsExactly("项目健康 ADS");
            });
        assertThat(page.toString()).doesNotContain("baseSql", "select ");
    }

    @Test
    void runtimeContract_shouldReturnOnlyPinnedPublishedSnapshot() {
        QueryDatasetAsset asset = asset();
        QueryDatasetVersion version = publishedVersion(asset);
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(versionRepository.findByDataset_IdAndVersionNo(asset.getId(), 2)).thenReturn(Optional.of(version));

        PublishedQueryDatasetService service = new PublishedQueryDatasetService(
            assetRepository,
            versionRepository,
            new ObjectMapper()
        );
        var contract = service.runtimeContract(asset.getId(), 2);

        assertThat(contract.status()).isEqualTo("PUBLISHED");
        assertThat(contract.baseSql()).isEqualTo(version.getSqlText());
        assertThat(contract.contractVersion()).isEqualTo("r7");
        assertThat(contract.contractChecksum()).isEqualTo(version.getSemanticContractChecksum());
        assertThat(contract.dimensions())
            .singleElement()
            .satisfies(field -> assertThat(field).containsEntry("code", "project_code"));
    }

    private QueryDatasetAsset asset() {
        QueryDatasetAsset asset = new QueryDatasetAsset();
        asset.setId(UUID.randomUUID());
        asset.setName("项目健康分析");
        asset.setDescription("项目健康主题已发布数据集");
        asset.setOwnerDept("D1");
        asset.setSourceDatasourceId(UUID.randomUUID());
        asset.setSourceDatasourceName("biadmin");
        asset.setRefreshStrategy("MANUAL");
        asset.setStatus("PUBLISHED");
        asset.setPublishedVersion(2);
        asset.setEnabled(true);
        asset.setCreatedBy("alice");
        asset.setSqlText("select project_code from ads_project_health");
        return asset;
    }

    private QueryDatasetVersion publishedVersion(QueryDatasetAsset asset) {
        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setId(UUID.randomUUID());
        version.setDataset(asset);
        version.setVersionNo(2);
        version.setStatus("PUBLISHED");
        version.setSqlText(asset.getSqlText());
        version.setSemanticContractSchema("dts.query-dataset-contract/v1");
        version.setSemanticContractVersion("r7");
        version.setContractSnapshotStatus("READY");
        version.setSemanticContractJson(
            "{\"contractVersion\":\"r7\",\"dimensions\":[{\"code\":\"project_code\"}],\"metrics\":[],\"joins\":[],\"sourceModels\":[{\"reference\":\"ads_project_health\",\"modelName\":\"项目健康 ADS\",\"modelRevision\":7}],\"classificationFloor\":\"DATA_INTERNAL\",\"policyRefs\":[]}"
        );
        version.setSemanticContractChecksum(PublishedQueryDatasetService.checksum(version.getSemanticContractJson(), version.getSqlText()));
        return version;
    }

    private void authenticateMaintainer() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "maintainer",
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_DEPT_DATA_OWNER")
            )
        );
    }
}
