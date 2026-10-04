package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentityType;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DerivationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.ResolvedSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryDatasetContractSnapshotAssemblerTest {

    private final QueryDatasetContractSnapshotAssembler assembler = new QueryDatasetContractSnapshotAssembler(
        new ObjectMapper()
    );

    @Test
    void assemble_shouldProduceDeterministicReadySnapshotFromPublishedInputs() {
        UUID datasetId = UUID.randomUUID();
        UUID modelSpecId = UUID.randomUUID();
        UUID implementationId = UUID.randomUUID();
        UUID classificationSnapshotId = UUID.randomUUID();

        QueryDatasetAsset asset = new QueryDatasetAsset();
        asset.setId(datasetId);
        asset.setName("项目健康分析");
        asset.setSourceDatasourceId(UUID.randomUUID());
        asset.setSourceDatasourceName("biadmin");

        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setDataset(asset);
        version.setVersionNo(3);
        version.setSqlText("select project_code, project_name, total_amount from {{ ref('ads_project_health') }}");

        ResultSet resultSet = new ResultSet();
        resultSet.setColumns("project_code,project_name,total_amount");

        ModelIdentity model = new ModelIdentity(
            ModelIdentityType.DBT_MODEL,
            implementationId,
            modelSpecId,
            implementationId,
            "项目健康 ADS",
            7,
            "model.pjm.ads_project_health"
        );
        DerivationResult classification = new DerivationResult(
            "REPORT",
            "bi-dataset:" + datasetId,
            "DATA_SENSITIVE",
            classificationSnapshotId,
            4,
            List.of(new ResolvedSource("ASSET", model.assetKey(), classificationSnapshotId, 4, "DATA_SENSITIVE")),
            null,
            List.of()
        );

        QueryDatasetContractSnapshotAssembler.Snapshot first = assembler.assemble(
            asset,
            version,
            resultSet,
            Map.of("ads_project_health", model),
            classification
        );
        QueryDatasetContractSnapshotAssembler.Snapshot second = assembler.assemble(
            asset,
            version,
            resultSet,
            Map.of("ads_project_health", model),
            classification
        );

        assertThat(first.schema()).isEqualTo("dts.query-dataset-contract/v1");
        assertThat(first.version()).isEqualTo("r7");
        assertThat(first.status()).isEqualTo("READY");
        assertThat(first.contractJson())
            .contains("\"classificationFloor\":\"DATA_SENSITIVE\"")
            .contains("\"project_code\"")
            .contains("\"code\":\"record_count\"")
            .contains("\"aggregation\":\"COUNT\"")
            .contains("\"modelRevision\":7")
            .contains(classificationSnapshotId.toString());
        assertThat(first.checksum()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(second.contractJson()).isEqualTo(first.contractJson());
        assertThat(second.checksum()).isEqualTo(first.checksum());
    }

    @Test
    void assemble_shouldMarkSnapshotUnresolvedWhenSemanticInputsAreIncomplete() {
        QueryDatasetAsset asset = new QueryDatasetAsset();
        asset.setId(UUID.randomUUID());
        asset.setName("不完整数据集");

        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setDataset(asset);
        version.setVersionNo(1);
        version.setSqlText("select 1");

        QueryDatasetContractSnapshotAssembler.Snapshot snapshot = assembler.assemble(
            asset,
            version,
            null,
            Map.of(),
            null
        );

        assertThat(snapshot.status()).isEqualTo("UNRESOLVED");
        assertThat(snapshot.contractJson()).contains("SEMANTIC_SOURCE_REQUIRED", "RESULT_SCHEMA_REQUIRED");
        assertThat(snapshot.checksum()).hasSize(64);
    }
}
