package com.yuzhi.dts.platform.service.goldenchain.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoldenChainIngestionTaskNormalizerTest {

    private final GoldenChainIngestionTaskNormalizer normalizer = new GoldenChainIngestionTaskNormalizer();

    @Test
    void jdbcTaskMapsToIngestionReadySnapshotAndOdsOutput() {
        GoldenChainIngestionTaskView view = normalizer.normalize(
            Map.of(
                "id",
                101,
                "name",
                "jdbc-orders-daily",
                "sourceType",
                "jdbc",
                "sourceDataSourceId",
                "11111111-1111-1111-1111-111111111111",
                "tableMapping",
                List.of(Map.of("source", "public.orders", "target", "ods.ods_orders")),
                "latestExecution",
                Map.of("id", 202, "status", "success", "checkpoint", "updated_at=2026-06-14T00:00:00Z")
            )
        );

        assertThat(view.sourceKind()).isEqualTo(GoldenChainSourceKind.JDBC);
        assertThat(view.taskRefType()).isEqualTo("ingestion_task");
        assertThat(view.taskRefId()).isEqualTo("101");
        assertThat(view.sourceRefType()).isEqualTo("infra_data_source");
        assertThat(view.sourceRefId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(view.odsOutputs()).containsExactly("ods.ods_orders");
        assertThat(view.checkpointRef()).isEqualTo("updated_at=2026-06-14T00:00:00Z");
        assertThat(view.stageSnapshot().stage()).isEqualTo(GoldenChainStage.INGESTION_READY);
        assertThat(view.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
        assertThat(view.stageSnapshot().evidenceRef()).isEqualTo("ingestion://tasks/101/executions/202");
    }

    @Test
    void apiTaskFailureMapsToBlockedIngestionWithoutLeakingSecrets() {
        GoldenChainIngestionTaskView view = normalizer.normalize(
            Map.of(
                "id",
                301,
                "name",
                "api-orders-daily",
                "sourceType",
                "api",
                "sourceDataSourceId",
                "22222222-2222-2222-2222-222222222222",
                "sourceConfig",
                Map.of("resource", Map.of("targetTable", "ods.ods_api_orders")),
                "destinationConfig",
                Map.of("username", "biadmin", "password", "ShouldNotLeak"),
                "latestExecution",
                Map.of("id", 302, "status", "failed", "errorMessage", "分页游标异常")
            )
        );

        assertThat(view.sourceKind()).isEqualTo(GoldenChainSourceKind.API);
        assertThat(view.odsOutputs()).containsExactly("ods.ods_api_orders");
        assertThat(view.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(view.stageSnapshot().blockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_INGESTION);
        assertThat(view.stageSnapshot().blockerReason()).isEqualTo("分页游标异常");
        assertThat(view.toString()).doesNotContain("ShouldNotLeak");
    }

    @Test
    void fileTaskUsesDestinationTableAsOdsOutput() {
        GoldenChainIngestionTaskView view = normalizer.normalize(
            Map.of(
                "id",
                401,
                "name",
                "file-orders-daily",
                "sourceType",
                "file",
                "sourceDataSourceId",
                "33333333-3333-3333-3333-333333333333",
                "destinationConfig",
                Map.of("table", List.of("ods.ods_file_orders")),
                "latestExecution",
                Map.of("id", 402, "status", "success")
            )
        );

        assertThat(view.sourceKind()).isEqualTo(GoldenChainSourceKind.FILE);
        assertThat(view.odsOutputs()).containsExactly("ods.ods_file_orders");
        assertThat(view.stageSnapshot().status()).isEqualTo(GoldenChainStageStatus.READY);
    }
}
