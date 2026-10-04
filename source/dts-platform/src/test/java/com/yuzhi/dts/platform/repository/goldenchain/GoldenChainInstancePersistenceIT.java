package com.yuzhi.dts.platform.repository.goldenchain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance;
import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainContract;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class GoldenChainInstancePersistenceIT {

    @Autowired
    private GoldenChainInstanceRepository instanceRepository;

    @Autowired
    private GoldenChainStageSnapshotRecordRepository snapshotRepository;

    @Test
    void savesJdbcGoldenChainWithCompleteStageSnapshots() {
        GoldenChainInstance instance = GoldenChainInstance.create(
            "jdbc-orders-daily",
            "订单 JDBC 黄金链路",
            GoldenChainSourceKind.JDBC,
            "数据治理负责人"
        );
        instance.setSourceRef("infra_data_source", UUID.randomUUID().toString());
        instance.setCurrentStage(GoldenChainStage.CONSUMABLE);
        instance.setLifecycleStatus(GoldenChainStageStatus.READY);
        instanceRepository.saveAndFlush(instance);

        List<GoldenChainStageSnapshotRecord> snapshots = GoldenChainContract
            .stages()
            .stream()
            .map(stage ->
                GoldenChainStageSnapshotRecord.fromContract(
                    instance,
                    GoldenChainStageSnapshot.ready(
                        stage,
                        "数据治理负责人",
                        "evidence://jdbc-orders-daily/" + stage.name().toLowerCase()
                    )
                )
            )
            .toList();
        snapshotRepository.saveAllAndFlush(snapshots);

        List<GoldenChainStageSnapshotRecord> savedSnapshots = snapshotRepository.findByChainInstanceIdOrderByStageSequenceAsc(
            instance.getId()
        );

        assertThat(instanceRepository.findFirstByChainKey("jdbc-orders-daily")).contains(instance);
        assertThat(savedSnapshots).hasSize(GoldenChainContract.stages().size());
        assertThat(savedSnapshots).extracting(GoldenChainStageSnapshotRecord::getStage).containsExactlyElementsOf(GoldenChainContract.stages());
        assertThat(savedSnapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.getStatus()).isEqualTo(GoldenChainStageStatus.READY);
            assertThat(snapshot.getOwner()).isEqualTo("数据治理负责人");
            assertThat(snapshot.getEvidenceRef()).startsWith("evidence://jdbc-orders-daily/");
        });
    }

    @Test
    void attachesSprint38ApiIngestionEvidenceThroughGenericReferences() {
        GoldenChainInstance instance = GoldenChainInstance.create(
            "api-sprint-38-orders",
            "Sprint-38 API 入湖样例链路",
            GoldenChainSourceKind.API,
            "接口数据负责人"
        );
        instance.setSourceRef("infra_api_data_source", UUID.randomUUID().toString());
        instance.setCurrentStage(GoldenChainStage.INGESTION_READY);
        instance.setLifecycleStatus(GoldenChainStageStatus.BLOCKED);
        instanceRepository.saveAndFlush(instance);

        GoldenChainStageSnapshotRecord snapshot = GoldenChainStageSnapshotRecord.fromContract(
            instance,
            GoldenChainStageSnapshot.blocked(
                GoldenChainStage.INGESTION_READY,
                "接口数据负责人",
                GoldenChainBlockerCode.BLOCKED_INGESTION,
                "API 入湖执行器返回分页游标异常"
            )
        );
        snapshot.setEvidenceRef("airflow://dag/api_ingestion/orders/20260614");
        snapshot.setEvidenceType("AIRFLOW_DAG_RUN");
        snapshot.setSourceRef("api_ingestion_task", "api_orders_daily");
        snapshotRepository.saveAndFlush(snapshot);

        GoldenChainStageSnapshotRecord savedSnapshot = snapshotRepository
            .findFirstByChainInstanceIdAndStage(instance.getId(), GoldenChainStage.INGESTION_READY)
            .orElseThrow();

        assertThat(instanceRepository.findFirstBySourceKindAndSourceRefTypeAndSourceRefId(
                GoldenChainSourceKind.API,
                "infra_api_data_source",
                instance.getSourceRefId()
            ))
            .contains(instance);
        assertThat(savedSnapshot.getStatus()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(savedSnapshot.getBlockerCode()).isEqualTo(GoldenChainBlockerCode.BLOCKED_INGESTION);
        assertThat(savedSnapshot.getBlockerReason()).contains("分页游标");
        assertThat(savedSnapshot.getEvidenceType()).isEqualTo("AIRFLOW_DAG_RUN");
    }
}
