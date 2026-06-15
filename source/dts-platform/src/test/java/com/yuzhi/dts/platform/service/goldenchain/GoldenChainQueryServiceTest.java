package com.yuzhi.dts.platform.service.goldenchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance;
import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord;
import com.yuzhi.dts.platform.repository.goldenchain.GoldenChainInstanceRepository;
import com.yuzhi.dts.platform.repository.goldenchain.GoldenChainStageSnapshotRecordRepository;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainDetailResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoldenChainQueryServiceTest {

    @Mock
    private GoldenChainInstanceRepository instanceRepository;

    @Mock
    private GoldenChainStageSnapshotRecordRepository snapshotRepository;

    private GoldenChainQueryService service;

    @BeforeEach
    void setUp() {
        service = new GoldenChainQueryService(instanceRepository, snapshotRepository);
    }

    @Test
    void listChainsReturnsBusinessReadableSummaries() {
        GoldenChainInstance instance = chain("jdbc-orders-daily", GoldenChainSourceKind.JDBC);
        instance.setCurrentStage(GoldenChainStage.CONSUMABLE);
        instance.setLifecycleStatus(GoldenChainStageStatus.READY);
        when(instanceRepository.findByEnabledTrueOrderByLastModifiedDateDesc()).thenReturn(List.of(instance));

        var result = service.listChains();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chainKey()).isEqualTo("jdbc-orders-daily");
        assertThat(result.get(0).sourceKind()).isEqualTo(GoldenChainSourceKind.JDBC);
        assertThat(result.get(0).currentStage()).isEqualTo(GoldenChainStage.CONSUMABLE);
        assertThat(result.get(0).currentStageLabel()).isEqualTo("消费资产可用");
        assertThat(result.get(0).status()).isEqualTo(GoldenChainStageStatus.READY);
    }

    @Test
    void detailAggregatesOrderedStagesWithNextActions() {
        GoldenChainInstance instance = chain("api-sprint-38-orders", GoldenChainSourceKind.API);
        instance.setCurrentStage(GoldenChainStage.INGESTION_READY);
        instance.setLifecycleStatus(GoldenChainStageStatus.BLOCKED);
        GoldenChainStageSnapshotRecord source = snapshot(
            instance,
            GoldenChainStage.SOURCE_READY,
            GoldenChainStageStatus.READY,
            "evidence://api/source-ready",
            null,
            null
        );
        GoldenChainStageSnapshotRecord ingestion = snapshot(
            instance,
            GoldenChainStage.INGESTION_READY,
            GoldenChainStageStatus.BLOCKED,
            null,
            GoldenChainBlockerCode.BLOCKED_INGESTION,
            "API 入湖执行器返回分页游标异常"
        );
        when(instanceRepository.findFirstByChainKeyAndEnabledTrue("api-sprint-38-orders")).thenReturn(Optional.of(instance));
        when(snapshotRepository.findByChainInstanceIdOrderByStageSequenceAsc(instance.getId())).thenReturn(List.of(source, ingestion));

        Optional<GoldenChainDetailResponse> result = service.findDetailByChainKey("api-sprint-38-orders");

        assertThat(result).isPresent();
        GoldenChainDetailResponse detail = result.orElseThrow();
        assertThat(detail.chainKey()).isEqualTo("api-sprint-38-orders");
        assertThat(detail.stages()).extracting(stage -> stage.stage()).containsExactly(GoldenChainStage.SOURCE_READY, GoldenChainStage.INGESTION_READY);
        assertThat(detail.stages().get(1).status()).isEqualTo(GoldenChainStageStatus.BLOCKED);
        assertThat(detail.stages().get(1).failureReason()).isEqualTo("API 入湖执行器返回分页游标异常");
        assertThat(detail.stages().get(1).nextAction()).isEqualTo("检查入湖任务、调度实例和目标表写入");
    }

    @Test
    void missingOrHiddenChainReturnsEmptyForFailClosedResourceBehavior() {
        when(instanceRepository.findFirstByChainKeyAndEnabledTrue("hidden-chain")).thenReturn(Optional.empty());

        assertThat(service.findDetailByChainKey("hidden-chain")).isEmpty();
    }

    private GoldenChainInstance chain(String chainKey, GoldenChainSourceKind sourceKind) {
        GoldenChainInstance instance = GoldenChainInstance.create(chainKey, "黄金链路", sourceKind, "数据负责人");
        instance.setId(UUID.randomUUID());
        instance.setSourceRef(sourceKind.name().toLowerCase() + "_source", UUID.randomUUID().toString());
        return instance;
    }

    private GoldenChainStageSnapshotRecord snapshot(
        GoldenChainInstance instance,
        GoldenChainStage stage,
        GoldenChainStageStatus status,
        String evidenceRef,
        GoldenChainBlockerCode blockerCode,
        String blockerReason
    ) {
        GoldenChainStageSnapshotRecord snapshot = new GoldenChainStageSnapshotRecord();
        snapshot.setChainInstanceId(instance.getId());
        snapshot.setStage(stage);
        snapshot.setStatus(status);
        snapshot.setOwner("数据负责人");
        snapshot.setEvidenceRef(evidenceRef);
        snapshot.setBlockerCode(blockerCode);
        snapshot.setBlockerReason(blockerReason);
        return snapshot;
    }
}
